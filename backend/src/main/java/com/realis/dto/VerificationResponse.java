package com.realis.dto;

import java.time.Instant;

/**
 * Résultat de la vérification d'un fichier.
 *
 * Les deux vérifications sont présentées séparément, conformément à la spec :
 *  (a) integrityCheck : le hash SHA-256 correspond à l'enregistrement scellé
 *  (b) tsaCheck       : le jeton RFC 3161 est cryptographiquement valide
 *
 * Verdicts possibles :
 *  - AUTHENTIQUE : hash identique à l'enregistrement de référence. Le hash cryptographique
 *                  reste valide même si cet enregistrement a été supprimé logiquement par
 *                  son propriétaire (voir VerificationService.verifyAgainstRecord) : dans
 *                  ce cas, `record.deleted` vaut true et `record.warning` est renseigné.
 *                  Tout consommateur de cette réponse doit inspecter `record.deleted` avant
 *                  de traiter un verdict AUTHENTIQUE comme une preuve pleinement valide.
 *  - ALTERE      : hash différent de l'enregistrement de référence (ID fourni)
 *  - INCONNU     : aucun enregistrement trouvé pour ce hash, ou recordId inexistant
 */
public record VerificationResponse(
    Verdict              verdict,
    String               uploadedSha256,
    SealResponse         record,          // null si verdict = INCONNU
    IntegrityCheckResult integrityCheck,
    TsaCheckResult       tsaCheck         // null si jeton no-op ou INCONNU
) {

    public enum Verdict { AUTHENTIQUE, ALTERE, INCONNU }

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
