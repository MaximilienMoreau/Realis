package com.realis.service.timestamp;

import java.time.Instant;

/**
 * Implémentation no-op de TimestampAuthority.
 * Utilisée pendant le développement (incréments b).
 * Remplacée par FreeTsaTimestampAuthority à l'incrément c.
 *
 * IMPORTANT : ne jamais utiliser en production.
 * Le jeton retourné est vide et non vérifiable.
 */
public class NoOpTimestampAuthority implements TimestampAuthority {

    private static final String NO_OP_URL = "no-op://localhost";

    @Override
    public TimestampToken timestamp(byte[] sha256Bytes) {
        // Jeton vide — l'horodatage sera remplacé par FreeTSA à l'incrément c
        return new TimestampToken(new byte[0], Instant.now(), NO_OP_URL);
    }

    @Override
    public TsaVerificationResult verify(byte[] tokenDer, byte[] sha256Bytes) {
        return new TsaVerificationResult(
            false,
            null,
            "Jeton TSA non disponible (no-op — incrément c requis pour l'horodatage réel)"
        );
    }
}
