package com.realis.service.timestamp;

import com.realis.config.TsaProperties;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.*;
import org.junit.jupiter.api.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests de l'implémentation RFC 3161 avec une TSA auto-signée (sans réseau).
 *
 * Couvre :
 *  - Parsing d'un jeton TSP valide
 *  - Vérification de la signature TSA
 *  - Détection de hash altéré (fichier modifié après scellement)
 *  - Rejet d'un jeton corrompu
 */
@DisplayName("FreeTsaTimestampAuthority — vérification RFC 3161")
class FreeTsaTimestampAuthorityTest {

    // Jeton généré avec la TSA auto-signée de test
    private static byte[] validTokenDer;
    private static byte[] testSha256;
    private static FreeTsaTimestampAuthority tsAuthority;

    @BeforeAll
    static void setUpAll() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // Hash SHA-256 simulant un fichier vidéo scellé
        testSha256 = MessageDigest.getInstance("SHA-256")
            .digest("données vidéo de test Realis".getBytes(StandardCharsets.UTF_8));

        // Génération de la TSA de test auto-signée
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048);
        KeyPair tsaKeyPair = kpg.generateKeyPair();

        X509CertificateHolder tsaCertHolder = buildTsaCert(tsaKeyPair);
        X509Certificate tsaCert = new JcaX509CertificateConverter()
            .setProvider("BC").getCertificate(tsaCertHolder);

        // Construction du générateur de jetons TSP
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC").build(tsaKeyPair.getPrivate());

        DigestCalculatorProvider dcp = new JcaDigestCalculatorProviderBuilder()
            .setProvider("BC").build();

        DigestCalculator hashCalc = dcp.get(
            new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)
        );

        TimeStampTokenGenerator tokenGen = new TimeStampTokenGenerator(
            new JcaSignerInfoGeneratorBuilder(dcp).build(contentSigner, tsaCert),
            hashCalc,
            new ASN1ObjectIdentifier("1.3.6.1.4.1.99999.1")  // OID test
        );
        tokenGen.addCertificates(new JcaCertStore(List.of(tsaCert)));

        // Génération du jeton de test
        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        reqGen.setCertReq(true);
        TimeStampRequest req = reqGen.generate(TSPAlgorithms.SHA256, testSha256);
        org.bouncycastle.tsp.TimeStampToken token =
            tokenGen.generate(req, BigInteger.ONE, new Date());
        validTokenDer = token.getEncoded();

        // Authority sans certificat configuré → utilisera le cert embarqué dans le jeton
        TsaProperties props = new TsaProperties("test", "http://localhost:9999", "/nonexistent/cert.crt", 5000);
        tsAuthority = new FreeTsaTimestampAuthority(props);
    }

    @Test
    @DisplayName("Jeton valide + hash correct → résultat authentique")
    void verify_validToken_correctHash_returnsValid() {
        TsaVerificationResult result = tsAuthority.verify(validTokenDer, testSha256);

        assertThat(result.valid()).isTrue();
        assertThat(result.timestamp()).isNotNull();
        assertThat(result.message()).containsIgnoringCase("valide");
    }

    @Test
    @DisplayName("Hash altéré → résultat invalide (fichier modifié après scellement)")
    void verify_validToken_tamperedHash_returnsFailed() {
        byte[] tamperedHash = testSha256.clone();
        tamperedHash[0] ^= 0xFF;

        TsaVerificationResult result = tsAuthority.verify(validTokenDer, tamperedHash);

        assertThat(result.valid()).isFalse();
        assertThat(result.timestamp()).isNull();
    }

    @Test
    @DisplayName("Jeton corrompu → TimestampException")
    void verify_corruptedToken_throwsTimestampException() {
        byte[] garbage = "jeton invalide non DER".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> tsAuthority.verify(garbage, testSha256))
            .isInstanceOf(TimestampException.class);
    }

    @Test
    @DisplayName("Jeton tronqué → TimestampException")
    void verify_truncatedToken_throwsTimestampException() {
        byte[] truncated = new byte[validTokenDer.length / 2];
        System.arraycopy(validTokenDer, 0, truncated, 0, truncated.length);

        assertThatThrownBy(() -> tsAuthority.verify(truncated, testSha256))
            .isInstanceOf(TimestampException.class);
    }

    @Test
    @DisplayName("Le jeton contient bien le hash soumis")
    void validToken_containsExpectedHash() throws Exception {
        org.bouncycastle.tsp.TimeStampToken parsed =
            new org.bouncycastle.tsp.TimeStampToken(
                new org.bouncycastle.cms.CMSSignedData(validTokenDer)
            );
        byte[] embeddedHash = parsed.getTimeStampInfo().getMessageImprintDigest();

        assertThat(embeddedHash).isEqualTo(testSha256);
    }

    @Test
    @DisplayName("Le jeton contient un timestamp non nul")
    void validToken_containsTimestamp() throws Exception {
        org.bouncycastle.tsp.TimeStampToken parsed =
            new org.bouncycastle.tsp.TimeStampToken(
                new org.bouncycastle.cms.CMSSignedData(validTokenDer)
            );

        assertThat(parsed.getTimeStampInfo().getGenTime()).isNotNull();
    }

    /**
     * Test d'intégration réseau — désactivé par défaut (nécessite FreeTSA accessible).
     * Lancer manuellement avec : mvn test -Dtest=FreeTsaTimestampAuthorityTest#timestamp_realFreeTsa
     */
    @Test
    @Disabled("Requiert un accès réseau à FreeTSA (https://freetsa.org/tsr)")
    @DisplayName("[INTEGRATION] Horodatage réel via FreeTSA")
    void timestamp_realFreeTsa_returnsValidToken() throws Exception {
        TsaProperties realProps = new TsaProperties(
            "freetsa", "https://freetsa.org/tsr", "/nonexistent/cert.crt", 15000
        );
        FreeTsaTimestampAuthority real = new FreeTsaTimestampAuthority(realProps);

        byte[] hash = MessageDigest.getInstance("SHA-256")
            .digest("test intégration FreeTSA".getBytes(StandardCharsets.UTF_8));

        TimestampToken token = real.timestamp(hash);

        assertThat(token.tokenDer()).isNotEmpty();
        assertThat(token.timestamp()).isNotNull();
        assertThat(token.tsaUrl()).isEqualTo("https://freetsa.org/tsr");

        TsaVerificationResult result = real.verify(token.tokenDer(), hash);
        assertThat(result.valid()).isTrue();
        assertThat(result.timestamp()).isEqualTo(token.timestamp());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static X509CertificateHolder buildTsaCert(KeyPair keyPair) throws Exception {
        X500Name issuer  = new X500Name("CN=Test TSA Realis, O=Test, C=FR");
        Date notBefore   = new Date();
        Date notAfter    = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC").build(keyPair.getPrivate());

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            issuer, BigInteger.ONE, notBefore, notAfter, issuer, keyPair.getPublic()
        );
        // Extended Key Usage : timeStamping uniquement (OID 1.3.6.1.5.5.7.3.8)
        builder.addExtension(Extension.extendedKeyUsage, true,
            new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        return builder.build(signer);
    }
}
