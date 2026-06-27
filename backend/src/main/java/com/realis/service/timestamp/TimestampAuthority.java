package com.realis.service.timestamp;

/**
 * Abstraction de la Time Stamp Authority (RFC 3161 / TSP).
 *
 * Implémentations prévues :
 * - NoOpTimestampAuthority  : placeholder (incrément b)
 * - FreeTsaTimestampAuthority : FreeTSA gratuite (incrément c)
 * - EidasTsaTimestampAuthority : TSA qualifiée eIDAS (phase 2)
 */
public interface TimestampAuthority {

    /**
     * Soumet un hash SHA-256 à la TSA et retourne le jeton RFC 3161.
     *
     * @param sha256Bytes les 32 octets du hash SHA-256
     * @return jeton d'horodatage contenant le token DER brut et l'instant certifié
     * @throws TimestampException si la TSA est inaccessible ou retourne une erreur
     */
    TimestampToken timestamp(byte[] sha256Bytes) throws TimestampException;

    /**
     * Vérifie un jeton TSA de façon indépendante (sans accès à la base de données).
     *
     * @param tokenDer    jeton RFC 3161 brut (DER)
     * @param sha256Bytes hash SHA-256 que le jeton est supposé couvrir
     */
    TsaVerificationResult verify(byte[] tokenDer, byte[] sha256Bytes) throws TimestampException;
}
