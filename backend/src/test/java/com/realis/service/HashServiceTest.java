package com.realis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HashService : calcul SHA-256")
class HashServiceTest {

    private HashService hashService;

    @BeforeEach
    void setUp() {
        hashService = new HashService();
    }

    @Test
    @DisplayName("SHA-256 de la chaîne vide (vecteur RFC connu)")
    void sha256_emptyString() {
        String hash = hashService.sha256Hex(new byte[0]);
        // Vecteur SHA-256("") selon FIPS 180-4
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("SHA-256 de 'hello world' (vecteur connu)")
    void sha256_helloWorld() {
        byte[] input = "hello world".getBytes(StandardCharsets.UTF_8);
        String hash = hashService.sha256Hex(input);
        assertThat(hash).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }

    @Test
    @DisplayName("sha256AndCopy produit le même hash que sha256Hex")
    void sha256AndCopy_hashMatchesDirect() throws IOException {
        byte[] data = "données de test pour Realis".getBytes(StandardCharsets.UTF_8);

        String hashDirect = hashService.sha256Hex(data);

        ByteArrayOutputStream dest = new ByteArrayOutputStream();
        String hashStream = hashService.sha256AndCopy(new ByteArrayInputStream(data), dest);

        assertThat(hashStream).isEqualTo(hashDirect);
    }

    @Test
    @DisplayName("sha256AndCopy copie intégralement les octets vers la destination")
    void sha256AndCopy_copiesAllBytes() throws IOException {
        byte[] data = "contenu vidéo simulé".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream dest = new ByteArrayOutputStream();
        hashService.sha256AndCopy(new ByteArrayInputStream(data), dest);

        assertThat(dest.toByteArray()).isEqualTo(data);
    }

    @Test
    @DisplayName("Deux fichiers différents produisent des hashs différents")
    void sha256_differentInputs_differentHashes() {
        String hash1 = hashService.sha256Hex("fichier A".getBytes(StandardCharsets.UTF_8));
        String hash2 = hashService.sha256Hex("fichier B".getBytes(StandardCharsets.UTF_8));
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Un seul octet modifié change complètement le hash")
    void sha256_singleByteChange_differentHash() {
        byte[] original = "données originales".getBytes(StandardCharsets.UTF_8);
        byte[] tampered  = original.clone();
        tampered[0] = (byte) (tampered[0] ^ 0xFF);   // flip du premier octet

        assertThat(hashService.sha256Hex(original)).isNotEqualTo(hashService.sha256Hex(tampered));
    }

    @Test
    @DisplayName("hexToBytes est l'inverse de sha256Hex")
    void hexToBytes_roundTrip() {
        byte[] input = "test".getBytes(StandardCharsets.UTF_8);
        String hex   = hashService.sha256Hex(input);
        byte[] back  = hashService.hexToBytes(hex);

        assertThat(back).hasSize(32);
        assertThat(hashService.sha256Hex(input)).isEqualTo(hex);
    }
}
