package com.realis.service.timestamp;

import java.time.Instant;

/**
 * Résultat d'une demande d'horodatage RFC 3161.
 *
 * @param tokenDer  Jeton TSA brut (DER encoded) — à stocker tel quel pour vérification tierce
 * @param timestamp Instant certifié par la TSA
 * @param tsaUrl    URL de la TSA utilisée (traçabilité)
 */
public record TimestampToken(
    byte[] tokenDer,
    Instant timestamp,
    String tsaUrl
) {}
