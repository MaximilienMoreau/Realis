package com.realis.dto;

import java.time.Instant;

/** Integrity, trusted timestamp and availability are independent facts. */
public record VerificationResponse(
    Verdict              verdict,
    String               uploadedSha256,
    SealResponse         record,          // null si verdict = INCONNU
    IntegrityCheckResult integrityCheck,
    TsaCheckResult       tsaCheck         // null si jeton no-op ou INCONNU
) {

    public enum Verdict { VERIFIE, IDENTIQUE_SANS_HORODATAGE, ALTERE, INCONNU, SUPPRIME }

    public record IntegrityCheckResult(
        boolean passed,
        String  message
    ) {}

    public record TsaCheckResult(
        boolean valid,
        Instant timestamp,
        String  message,
        boolean isNoOp      // true si le jeton vient de la TSA no-op (avertissement, pas erreur)
    ) {}
}
