package com.realis.service.timestamp;

import java.time.Instant;

/**
 * Résultat de la vérification d'un jeton RFC 3161.
 *
 * @param valid     Le jeton est valide et correspond au hash fourni
 * @param timestamp Instant certifié par la TSA (null si invalid)
 * @param message   Message lisible décrivant le résultat ou l'erreur
 */
public record TsaVerificationResult(
    boolean valid,
    Instant timestamp,
    String message
) {}
