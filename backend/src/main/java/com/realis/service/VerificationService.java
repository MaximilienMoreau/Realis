package com.realis.service;

import com.realis.dto.SealResponse;
import com.realis.dto.VerificationResponse;
import com.realis.dto.VerificationResponse.*;
import com.realis.exception.ResourceNotFoundException;
import com.realis.model.SealedRecord;
import com.realis.repository.SealedRecordRepository;
import com.realis.service.timestamp.TimestampAuthority;
import com.realis.service.timestamp.TimestampException;
import com.realis.service.timestamp.TsaVerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final HashService             hashService;
    private final SealedRecordRepository  sealedRecordRepository;
    private final TimestampAuthority      timestampAuthority;

    /**
     * Vérifie l'intégrité et l'horodatage d'un fichier.
     *
     * Deux modes :
     *  - Avec recordId : compare le hash du fichier uploadé contre l'enregistrement spécifié
     *                    → AUTHENTIQUE ou ALTÉRÉ (verdict définitif)
     *  - Sans recordId : recherche par hash dans tous les enregistrements
     *                    → AUTHENTIQUE (trouvé) ou INCONNU (non trouvé)
     *
     * Le hash est calculé sur les octets bruts du fichier uploadé, sans ré-encodage.
     */
    public VerificationResponse verify(MultipartFile file, UUID recordId) throws IOException {
        String uploadedHash = hashService.sha256Hex(file.getInputStream());
        log.debug("Vérification : hash calculé = {}", uploadedHash);

        if (recordId != null) {
            return verifyAgainstRecord(uploadedHash, recordId);
        } else {
            return verifyByHash(uploadedHash);
        }
    }

    /**
     * Mode 1 : Vérification contre un enregistrement précis (identifié par son UUID).
     * Permet d'obtenir un verdict ALTÉRÉ définitif si le hash diffère.
     *
     * Si le recordId ne correspond à aucun enregistrement, on retombe sur le même
     * contrat que le mode 2 (verdict INCONNU) plutôt que de laisser fuiter une
     * ResourceNotFoundException : le client reçoit toujours un VerificationResponse.
     */
    private VerificationResponse verifyAgainstRecord(String uploadedHash, UUID recordId) {
        Optional<SealedRecord> found = sealedRecordRepository.findById(recordId);
        if (found.isEmpty()) {
            return new VerificationResponse(
                Verdict.INCONNU,
                uploadedHash,
                null,
                new IntegrityCheckResult(false,
                    "L'identifiant d'enregistrement fourni (" + recordId + ") n'existe pas."),
                null
            );
        }
        SealedRecord record = found.get();

        boolean hashMatches = uploadedHash.equalsIgnoreCase(record.getSha256Hex());
        Verdict verdict = hashMatches ? Verdict.AUTHENTIQUE : Verdict.ALTERE;

        IntegrityCheckResult integrity = hashMatches
            ? new IntegrityCheckResult(true,
                "Le hash SHA-256 du fichier correspond exactement à l'enregistrement scellé.")
            : new IntegrityCheckResult(false,
                "Le hash SHA-256 ne correspond PAS à l'enregistrement scellé. " +
                "Le fichier a été modifié depuis le scellement.");

        // La vérification TSA porte toujours sur le hash scellé (pas le hash uploadé altéré)
        TsaCheckResult tsaCheck = buildTsaCheck(record);

        return new VerificationResponse(verdict, uploadedHash, SealResponse.from(record), integrity, tsaCheck);
    }

    /**
     * Mode 2 : Recherche par hash (pas de référence fournie).
     * Retourne AUTHENTIQUE si un enregistrement actif correspond, INCONNU sinon.
     */
    private VerificationResponse verifyByHash(String uploadedHash) {
        Optional<SealedRecord> found = sealedRecordRepository.findActiveBySha256Hex(uploadedHash);

        if (found.isEmpty()) {
            return new VerificationResponse(
                Verdict.INCONNU,
                uploadedHash,
                null,
                new IntegrityCheckResult(false,
                    "Aucun enregistrement scellé par Realis ne correspond à ce fichier. " +
                    "Le fichier n'a peut-être jamais été scellé, ou son hash a changé."),
                null
            );
        }

        SealedRecord record = found.get();
        TsaCheckResult tsaCheck = buildTsaCheck(record);

        return new VerificationResponse(
            Verdict.AUTHENTIQUE,
            uploadedHash,
            SealResponse.from(record),
            new IntegrityCheckResult(true,
                "Le hash SHA-256 correspond à l'enregistrement scellé le " +
                record.getSealedAt() + "."),
            tsaCheck
        );
    }

    /**
     * Vérifie le jeton TSA de l'enregistrement.
     * Si le jeton est vide (no-op), retourne un avertissement sans invalider le verdict.
     */
    private TsaCheckResult buildTsaCheck(SealedRecord record) {
        boolean isNoOp = record.getTsaUrl().startsWith("no-op://");

        if (isNoOp || record.getTsaTokenDer().length == 0) {
            return new TsaCheckResult(false, null,
                "Horodatage TSA non disponible pour cet enregistrement (mode développement). " +
                "L'horodatage RFC 3161 sera actif en production.",
                true);
        }

        try {
            byte[] sha256Bytes = hashService.hexToBytes(record.getSha256Hex());
            TsaVerificationResult result = timestampAuthority.verify(record.getTsaTokenDer(), sha256Bytes);
            return new TsaCheckResult(result.valid(), result.timestamp(), result.message(), false);
        } catch (TimestampException e) {
            log.warn("Erreur de vérification TSA pour l'enregistrement {}", record.getId(), e);
            return new TsaCheckResult(false, null,
                "Erreur lors de la vérification du jeton TSA : " + e.getMessage(),
                false);
        }
    }

    /**
     * Retourne le jeton TSA brut (DER) d'un enregistrement.
     * Permet une vérification indépendante sans accès à la base Realis.
     *
     * Exemple openssl :
     *   openssl ts -verify -in token.tsr -data fichier.webm -CAfile freetsa-ca.crt
     */
    public byte[] getTsaToken(UUID recordId) {
        SealedRecord record = sealedRecordRepository.findById(recordId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Enregistrement introuvable : " + recordId
            ));
        return record.getTsaTokenDer();
    }

    /** Retourne les métadonnées publiques d'un enregistrement (sans les données de l'utilisateur). */
    public SealResponse getPublicMetadata(UUID recordId) {
        SealedRecord record = sealedRecordRepository.findById(recordId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Enregistrement introuvable : " + recordId
            ));
        return SealResponse.from(record);
    }
}
