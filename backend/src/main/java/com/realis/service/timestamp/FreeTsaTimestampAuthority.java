package com.realis.service.timestamp;

import com.realis.config.TsaProperties;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.tsp.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
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
    private final SecureRandom rng = new SecureRandom();

    public FreeTsaTimestampAuthority(TsaProperties props) {
        this.props = props;
    }

    @Override
    public TimestampToken timestamp(byte[] sha256Bytes) throws TimestampException {
        try {
            // Requête RFC 3161 : certReq=true pour recevoir le cert TSA dans la réponse
            TimeStampRequestGenerator gen = new TimeStampRequestGenerator();
            gen.setCertReq(true);
            BigInteger nonce = new BigInteger(64, rng);
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

            log.info("Horodatage obtenu : certifié le {} par {}", tsaInstant, props.url());
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
     * Trois vérifications distinctes :
     *  (a) Intégrité du fichier : le hash dans le jeton == sha256Bytes fourni.
     *  (b) Chaîne de confiance : le certificat signataire embarqué dans le jeton est bien
     *      émis par l'autorité configurée localement (props.certPath(), la racine CA
     *      FreeTSA). Sans cette étape, n'importe quel certificat auto-signé embarqué dans
     *      le jeton (contrôlé par l'émetteur du jeton, pas par Realis) serait accepté :
     *      un jeton entièrement forgé passerait la vérification de signature (b) suivante
     *      puisqu'elle porterait sur une clé que l'attaquant a lui-même fournie.
     *  (c) Signature : la signature CMS du jeton correspond bien au certificat signataire.
     *
     * Le certificat utilisé pour vérifier la signature CMS (c) est toujours celui
     * embarqué dans le jeton (présent car certReq=true à la demande) : c'est la seule clé
     * publique disponible pour cette vérification. Ce que (b) garantit, c'est que ce
     * certificat descend bien de l'autorité de confiance configurée, et non d'un tiers.
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

            Collection<SignerInformation> signers = signedData.getSignerInfos().getSigners();
            if (signers.isEmpty()) {
                return new TsaVerificationResult(false, null,
                    "Aucun signataire dans le jeton TSA : jeton invalide.");
            }
            SignerInformation si = signers.iterator().next();

            X509CertificateHolder signerCert = extractEmbeddedSignerCert(signedData, si);
            if (signerCert == null) {
                return new TsaVerificationResult(false, null,
                    "Certificat signataire introuvable dans le jeton TSA : impossible de vérifier la signature.");
            }

            // (b) Chaîne de confiance vers l'autorité configurée localement
            X509CertificateHolder trustedRoot = loadTrustedRoot();
            if (trustedRoot != null) {
                if (!isIssuedBy(signerCert, trustedRoot)) {
                    return new TsaVerificationResult(false, null,
                        "Le certificat signataire du jeton TSA n'est pas émis par l'autorité de " +
                        "confiance configurée (" + props.certPath() + ") : jeton rejeté.");
                }
            } else {
                log.warn("Aucune ancre de confiance TSA lisible ({}) : le certificat embarqué dans " +
                    "le jeton est utilisé sans validation de chaîne. À corriger avant mise en production.",
                    props.certPath());
            }

            // (c) Vérification de la signature du jeton
            bcToken.validate(
                new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(signerCert)
            );

            Instant tsaInstant = bcToken.getTimeStampInfo().getGenTime().toInstant();
            return new TsaVerificationResult(true, tsaInstant,
                "Jeton TSA valide : horodatage certifié le " + tsaInstant + " par " + props.url());

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

    /** Certificat effectivement utilisé pour signer le jeton (embarqué, car certReq=true à la demande). */
    @SuppressWarnings("unchecked")
    private X509CertificateHolder extractEmbeddedSignerCert(CMSSignedData signedData, SignerInformation si) {
        Collection<X509CertificateHolder> matches =
            (Collection<X509CertificateHolder>) signedData.getCertificates().getMatches(si.getSID());
        return matches.isEmpty() ? null : matches.iterator().next();
    }

    /** Ancre de confiance locale (racine CA FreeTSA), si le fichier configuré existe et est lisible. */
    private X509CertificateHolder loadTrustedRoot() {
        Path certPath = Path.of(props.certPath());
        if (!Files.exists(certPath)) {
            return null;
        }
        try {
            return new X509CertificateHolder(Files.readAllBytes(certPath));
        } catch (Exception e) {
            log.warn("Impossible de charger le certificat de confiance TSA configuré ({})", props.certPath(), e);
            return null;
        }
    }

    /** Vérifie que `cert` a bien été signé par la clé privée correspondant à `issuer`. */
    private boolean isIssuedBy(X509CertificateHolder cert, X509CertificateHolder issuer) {
        try {
            return cert.isSignatureValid(
                new JcaContentVerifierProviderBuilder().setProvider("BC").build(issuer)
            );
        } catch (Exception e) {
            log.warn("Erreur lors de la vérification de la chaîne de certificats TSA", e);
            return false;
        }
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
