package com.realis.service;

import com.realis.dto.SealRequest;
import com.realis.dto.SealResponse;
import com.realis.exception.ConflictException;
import com.realis.exception.ResourceNotFoundException;
import com.realis.model.ConsentLog;
import com.realis.model.SealedRecord;
import com.realis.model.User;
import com.realis.repository.ConsentLogRepository;
import com.realis.repository.SealedRecordRepository;
import com.realis.repository.UserRepository;
import com.realis.service.timestamp.TimestampAuthority;
import com.realis.service.timestamp.TimestampToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SealingService {

    private final HashService hashService;
    private final StorageService storageService;
    private final TimestampAuthority timestampAuthority;
    private final UserRepository userRepository;
    private final ConsentLogRepository consentLogRepository;
    private final SealedRecordRepository sealedRecordRepository;

    /**
     * Flux de scellement complet :
     * 1. Calcul du SHA-256 sur les octets bruts du fichier uploadé
     * 2. Stockage chiffré (AES-256-GCM) sur volume
     * 3. Demande d'horodatage RFC 3161 à la TSA
     * 4. Création de l'enregistrement immuable en base
     *
     * Le hash porte sur les octets exacts tels qu'uploadés.
     * Aucun ré-encodage n'est effectué.
     *
     * rollbackFor = Exception.class : par défaut, Spring ne fait rollback que sur les
     * RuntimeException. Cette méthode déclare `throws IOException` (checked) et peut
     * échouer en cours de route après avoir déjà sauvegardé le ConsentLog (ligne
     * createConsentLog) : sans rollbackFor, une IOException (disque plein, volume en
     * lecture seule...) commiterait ce ConsentLog orphelin au lieu de l'annuler.
     */
    @Transactional(rollbackFor = Exception.class)
    public SealResponse seal(MultipartFile file, UUID userId, SealRequest request, String clientIp) throws IOException {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + userId));

        // Enregistrement du consentement
        ConsentLog consent = createConsentLog(user, request, clientIp);

        UUID recordId = UUID.randomUUID();

        // Calcul du hash + copie vers fichier temporaire en un seul passage
        Path tempFile = Files.createTempFile("realis-seal-", ".tmp");
        String sha256Hex;
        long fileSize;
        String storagePath = null;

        try {
            try (OutputStream tempOut = Files.newOutputStream(tempFile)) {
                sha256Hex = hashService.sha256AndCopy(file.getInputStream(), tempOut);
            }
            fileSize = Files.size(tempFile);

            log.info("Scellement {} : hash SHA-256 calculé ({} octets)", recordId, fileSize);

            // Horodatage RFC 3161 avant le stockage chiffré (no-op en incrément b, FreeTSA
            // en incrément c) : si la TSA échoue, on évite d'écrire un fichier chiffré sur
            // le volume qui resterait orphelin (le rollback transactionnel n'annule que les
            // écritures DB, jamais une écriture disque déjà effectuée).
            byte[] sha256Bytes = hashService.hexToBytes(sha256Hex);
            TimestampToken tsaToken = timestampAuthority.timestamp(sha256Bytes);

            log.info("Scellement {} : horodatage obtenu (TSA : {})", recordId, tsaToken.tsaUrl());

            // Chiffrement et stockage sur volume
            storagePath = storageService.encryptAndStore(tempFile, user.getId(), recordId);

            // Création de l'enregistrement immuable
            SealedRecord record = SealedRecord.builder()
                .id(recordId)
                .user(user)
                .consentLog(consent)
                .fileName(resolveFileName(file))
                .fileSizeBytes(fileSize)
                .mimeType(resolveMimeType(file, request))
                .sha256Hex(sha256Hex)
                .tsaTokenDer(tsaToken.tokenDer())
                .tsaUrl(tsaToken.tsaUrl())
                .tsaTimestamp(tsaToken.timestamp())
                .geolocLat(request.geolocLat())
                .geolocLng(request.geolocLng())
                .deviceUa(request.deviceUa())
                .storagePath(storagePath)
                .build();

            sealedRecordRepository.save(record);

            log.info("Scellement {} terminé avec succès", recordId);
            return SealResponse.from(record);

        } catch (Exception e) {
            // Le fichier a pu être écrit sur le volume juste avant un échec plus tardif
            // (ex. violation de contrainte DB à la sauvegarde) : on le nettoie explicitement
            // pour ne pas laisser de fichier chiffré sans enregistrement associé.
            if (storagePath != null) {
                Files.deleteIfExists(Path.of(storagePath));
            }
            throw e;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Réservé au propriétaire : la page publique de vérification utilise
     * VerificationService.getPublicMetadata, qui ne nécessite pas d'auth.
     */
    @Transactional(readOnly = true)
    public SealResponse findByIdForOwner(UUID id, UUID requestingUserId) {
        SealedRecord record = sealedRecordRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Enregistrement introuvable : " + id));
        if (!record.getUser().getId().equals(requestingUserId)) {
            throw new SecurityException("Accès refusé");
        }
        return SealResponse.from(record);
    }

    /**
     * Liste les scellements actifs du propriétaire, du plus récent au plus ancien.
     */
    @Transactional(readOnly = true)
    public List<SealResponse> listForOwner(UUID requestingUserId) {
        return sealedRecordRepository.findActiveByUserId(requestingUserId).stream()
            .map(SealResponse::from)
            .toList();
    }

    /**
     * Suppression logique : invalide la preuve.
     * Un avertissement explicite est inclus dans la réponse.
     */
    @Transactional
    public SealResponse softDelete(UUID id, UUID requestingUserId) {
        SealedRecord record = sealedRecordRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Enregistrement introuvable : " + id));

        if (!record.getUser().getId().equals(requestingUserId)) {
            throw new SecurityException("Accès refusé");
        }
        if (record.getDeletedAt() != null) {
            throw new ConflictException("Enregistrement déjà supprimé");
        }

        record.setDeletedAt(Instant.now());
        sealedRecordRepository.save(record);

        return SealResponse.from(record);
    }

    private ConsentLog createConsentLog(User user, SealRequest request, String clientIp) {
        ConsentLog consent = ConsentLog.builder()
            .user(user)
            .sessionId(UUID.randomUUID().toString())
            .geolocConsented(request.geolocLat() != null && request.geolocLng() != null)
            .purposeText("État des lieux : scellement de capture (MVP). " +
                         "Conservation 365 jours à des fins de preuve d'intégrité.")
            .retentionDays(365)
            .userAgent(request.deviceUa())
            .ipAddress(clientIp)
            .build();
        return consentLogRepository.save(consent);
    }

    private String resolveFileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name != null && !name.isBlank()) ? name : "capture.bin";
    }

    private String resolveMimeType(MultipartFile file, SealRequest request) {
        if (request.mimeType() != null && !request.mimeType().isBlank()) {
            return request.mimeType();
        }
        String ct = file.getContentType();
        return (ct != null && !ct.isBlank()) ? ct : "application/octet-stream";
    }
}
