package com.realis.service.timestamp;

import com.realis.config.TsaProperties;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.tsp.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;

/**
 * Implémentation RFC 3161 (TSP) utilisant FreeTSA comme autorité d'horodatage.
 *
 * Protocole :
 *   1. Construit une TimeStampRequest avec SHA-256 hash + nonce
 *   2. HTTP POST vers la TSA (application/timestamp-query)
 *   3. Parse la TimeStampResponse et valide le status
 *   4. Retourne le jeton DER brut + l'instant certifié
 *
 * Vérification indépendante (sans accès à la DB Realis) :
 *   openssl ts -verify -in token.tsr -data fichier_original.webm -CAfile freetsa-ca.crt
 *
 * La classe peut aussi être utilisée avec toute TSA compatible RFC 3161
 * en changeant TSA_URL dans la configuration.
 */
@Slf4j
public class FreeTsaTimestampAuthority implements TimestampAuthority {

    // 0 = GRANTED, 1 = GRANTED_WITH_MODS (RFC 3161 §2.4.2)
    private static final int STATUS_GRANTED           = 0;
    private static final int STATUS_GRANTED_WITH_MODS = 1;

    private final TsaProperties props;

    public FreeTsaTimestampAuthority(TsaProperties props) {
        this.props = props;
    }

    @Override
    public TimestampToken timestamp(byte[] sha256Bytes) throws TimestampException {
        try {
            // Requête RFC 3161 : certReq=true pour recevoir le cert TSA dans la réponse
            TimeStampRequestGenerator gen = new TimeStampRequestGenerator();
            gen.setCertReq(true);
            BigInteger nonce = BigInteger.valueOf(System.currentTimeMillis());
            TimeStampRequest request = gen.generate(TSPAlgorithms.SHA256, sha256Bytes, nonce);

            log.debug("Envoi requête TSA à {} ({} octets)", props.url(), request.getEncoded().length);

            byte[] responseDer = sendHttpPost(request.getEncoded());

            TimeStampResponse response = new TimeStampResponse(responseDer);
            // Vérifie la cohérence de la réponse par rapport à la requête
            response.validate(request);

            int status = response.getStatus();
            if (status != STATUS_GRANTED && status != STATUS_GRANTED_WITH_MODS) {
                throw new TimestampException(
                    "TSA a rejeté la demande (status=" + status + ") : " + response.getStatusString()
                );
            }

            org.bouncycastle.tsp.TimeStampToken bcToken = response.getTimeStampToken();
            byte[] tokenDer    = bcToken.getEncoded();
            Instant tsaInstant = bcToken.getTimeStampInfo().getGenTime().toInstant();

            log.info("Horodatage obtenu — certifié le {} par {}", tsaInstant, props.url());
            return new TimestampToken(tokenDer, tsaInstant, props.url());

        } catch (TimestampException e) {
            throw e;
        } catch (IOException e) {
            throw new TimestampException("Impossible de joindre la TSA : " + props.url(), e);
        } catch (TSPException e) {
            throw new TimestampException("Erreur de protocole TSP : " + e.getMessage(), e);
        } catch (Exception e) {
            throw new TimestampException("Erreur inattendue lors de l'horodatage", e);
        }
    }

    /**
     * Vérifie un jeton RFC 3161 de façon indépendante.
     *
     * Deux vérifications distinctes :
     *  (a) Intégrité du fichier : le hash dans le jeton == sha256Bytes fourni
     *  (b) Horodatage : la signature du jeton est valide (via cert TSA)
     *
     * Pour la résolution du certificat :
     *  - Priorité 1 : certificat de confiance configuré localement (trust anchor)
     *  - Priorité 2 : certificat embarqué dans le jeton (présent si certReq=true)
     */
    @Override
    public TsaVerificationResult verify(byte[] tokenDer, byte[] sha256Bytes) throws TimestampException {
        try {
            CMSSignedData signedData = new CMSSignedData(tokenDer);
            org.bouncycastle.tsp.TimeStampToken bcToken =
                new org.bouncycastle.tsp.TimeStampToken(signedData);

            // (a) Vérification de l'intégrité du fichier
            byte[] tokenHash = bcToken.getTimeStampInfo().getMessageImprintDigest();
            if (!Arrays.equals(tokenHash, sha256Bytes)) {
                return new TsaVerificationResult(false, null,
                    "Le hash dans le jeton TSA ne correspond pas au hash du fichier : " +
                    "le fichier a peut-être été altéré après l'horodatage.");
            }

            // (b) Vérification de la signature du jeton
            Collection<SignerInformation> signers = signedData.getSignerInfos().getSigners();
            if (signers.isEmpty()) {
                return new TsaVerificationResult(false, null,
                    "Aucun signataire dans le jeton TSA : jeton invalide.");
            }
            SignerInformation si = signers.iterator().next();

            X509CertificateHolder signerCert = resolveSignerCert(signedData, si);
            if (signerCert == null) {
                return new TsaVerificationResult(false, null,
                    "Certificat TSA introuvable — impossible de vérifier la signature du jeton.");
            }

            bcToken.validate(
                new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(signerCert)
            );

            Instant tsaInstant = bcToken.getTimeStampInfo().getGenTime().toInstant();
            return new TsaVerificationResult(true, tsaInstant,
                "Jeton TSA valide — horodatage certifié le " + tsaInstant + " par " + props.url());

        } catch (TSPValidationException e) {
            // La signature TSA est cryptographiquement invalide
            return new TsaVerificationResult(false, null,
                "Signature TSA invalide : " + e.getMessage());
        } catch (CMSException | TSPException e) {
            throw new TimestampException("Jeton TSA malformé ou corrompu", e);
        } catch (Exception e) {
            throw new TimestampException("Erreur lors de la vérification du jeton TSA", e);
        }
    }

    /**
     * Résout le certificat du signataire TSA.
     * Priorité 1 : fichier de confiance configuré (trust anchor local).
     * Priorité 2 : certificat embarqué dans le jeton (certReq=true lors de la demande).
     */
    @SuppressWarnings("unchecked")
    private X509CertificateHolder resolveSignerCert(CMSSignedData signedData, SignerInformation si) {
        Path certPath = Path.of(props.certPath());
        if (Files.exists(certPath)) {
            try {
                return new X509CertificateHolder(Files.readAllBytes(certPath));
            } catch (Exception e) {
                log.warn("Impossible de charger le certificat TSA configuré ({}), utilisation du cert embarqué",
                    props.certPath(), e);
            }
        }

        // Fallback : cert embarqué dans la structure CMS du jeton
        Collection<X509CertificateHolder> matches =
            (Collection<X509CertificateHolder>) signedData.getCertificates().getMatches(si.getSID());
        if (!matches.isEmpty()) {
            return matches.iterator().next();
        }

        return null;
    }

    private byte[] sendHttpPost(byte[] body) throws IOException {
        @SuppressWarnings("deprecation")
        HttpURLConnection conn = (HttpURLConnection) new URL(props.url()).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/timestamp-query");
            conn.setRequestProperty("Accept", "application/timestamp-reply");
            conn.setConnectTimeout(props.timeoutMs());
            conn.setReadTimeout(props.timeoutMs());
            conn.setDoOutput(true);

            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("TSA a retourné HTTP " + code + " (attendu 200)");
            }

            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }
        } finally {
            conn.disconnect();
        }
    }
}
