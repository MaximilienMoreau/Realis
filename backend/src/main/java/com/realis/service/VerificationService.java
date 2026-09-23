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
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final HashService             hashService;
    private final SealedRecordRepository  sealedRecordRepository;
    private final TimestampAuthority      timestampAuthority;


    public VerificationResponse verify(MultipartFile file, UUID recordId) throws IOException {
        String uploadedHash;
        try (InputStream in = file.getInputStream()) {
            uploadedHash = hashService.sha256Hex(in);
        }
        log.debug("Vérification : hash calculé = {}", uploadedHash);

        if (recordId != null) {
            return verifyAgainstRecord(uploadedHash, recordId);
        } else {
            return verifyByHash(uploadedHash);
        }
    }


    private VerificationResponse verifyAgainstRecord(String uploadedHash, UUID recordId) {
        Optional<SealedRecord> found = sealedRecordRepository.findById(recordId);
        if (found.isEmpty()) {
            if (sealedRecordRepository.wasDeleted(recordId)) return deleted(uploadedHash);
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

        if (!record.isAvailable()) return deleted(uploadedHash);

        boolean hashMatches = uploadedHash.equalsIgnoreCase(record.getSha256Hex());
        Verdict verdict = hashMatches ? Verdict.IDENTIQUE_SANS_HORODATAGE : Verdict.ALTERE;

        IntegrityCheckResult integrity = hashMatches
            ? new IntegrityCheckResult(true,
                "Le hash SHA-256 du fichier correspond exactement à l'enregistrement scellé.")
            : new IntegrityCheckResult(false,
                "Le hash SHA-256 ne correspond PAS à l'enregistrement scellé. " +
                "Le fichier soumis diffère du fichier de référence.");

        // La vérification TSA porte toujours sur le hash scellé (pas le hash uploadé altéré)
        TsaCheckResult tsaCheck = buildTsaCheck(record);

        if (hashMatches && tsaCheck.valid()) verdict = Verdict.VERIFIE;
        return new VerificationResponse(verdict, uploadedHash, SealResponse.from(record), integrity, tsaCheck);
    }


    private VerificationResponse verifyByHash(String uploadedHash) {
        List<SealedRecord> found = sealedRecordRepository.findActiveBySha256Hex(uploadedHash).stream().filter(SealedRecord::isAvailable).toList();

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

        SealedRecord record = found.get(0);
        TsaCheckResult tsaCheck = buildTsaCheck(record);

        return new VerificationResponse(
            tsaCheck.valid() ? Verdict.VERIFIE : Verdict.IDENTIQUE_SANS_HORODATAGE,
            uploadedHash,
            SealResponse.from(record),
            new IntegrityCheckResult(true,
                "Le hash SHA-256 correspond à l'enregistrement scellé le " +
                record.getSealedAt() + "."),
            tsaCheck
        );
    }


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


    public byte[] getTsaToken(UUID recordId) {
        SealedRecord record = sealedRecordRepository.findById(recordId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Enregistrement introuvable : " + recordId
            ));
        requireAvailable(record);
        return record.getTsaTokenDer();
    }


    public SealResponse getPublicMetadata(UUID recordId) {
        SealedRecord record = sealedRecordRepository.findById(recordId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Enregistrement introuvable : " + recordId
            ));
        requireAvailable(record);
        return SealResponse.from(record);
    }
    public static void requireAvailable(SealedRecord record) {
        if (!record.isAvailable()) throw new ResourceNotFoundException("Preuve supprimée ou expirée");
    }
    private VerificationResponse deleted(String hash) {
        return new VerificationResponse(Verdict.SUPPRIME, hash, null,
            new IntegrityCheckResult(false, "Enregistrement supprimé ou expiré. Données indisponibles."), null);
    }
}
