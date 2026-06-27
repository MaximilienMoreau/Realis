package com.realis.service;

import com.realis.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StorageService — chiffrement AES-256-GCM")
class StorageServiceTest {

    // Clé AES-256 de test (32 octets, NE PAS réutiliser en production)
    private static final String TEST_KEY_BASE64 =
        Base64.getEncoder().encodeToString(new byte[32]);

    @TempDir
    Path tempDir;

    private StorageService storageService;

    @BeforeEach
    void setUp() throws IOException {
        StorageProperties props = new StorageProperties(tempDir.toString(), TEST_KEY_BASE64);
        storageService = new StorageService(props);
        storageService.init();
    }

    @Test
    @DisplayName("Chiffrement puis déchiffrement retourne les octets originaux")
    void encryptDecrypt_roundTrip() throws IOException {
        byte[] original = "contenu vidéo de test 1234567890".getBytes();
        UUID userId   = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

        Path tempFile = Files.createTempFile(tempDir, "test-", ".tmp");
        Files.write(tempFile, original);

        String storagePath = storageService.encryptAndStore(tempFile, userId, recordId);
        byte[] decrypted   = storageService.decryptToBytes(storagePath);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("Le fichier chiffré sur disque est différent du contenu original")
    void encryptedFile_differsFromPlaintext() throws IOException {
        byte[] original = "données sensibles de test".getBytes();
        UUID userId   = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

        Path tempFile = Files.createTempFile(tempDir, "test-", ".tmp");
        Files.write(tempFile, original);

        String storagePath = storageService.encryptAndStore(tempFile, userId, recordId);
        byte[] onDisk      = Files.readAllBytes(Path.of(storagePath));

        // Le fichier chiffré ne doit pas contenir le texte en clair
        assertThat(onDisk).isNotEqualTo(original);
    }

    @Test
    @DisplayName("Deux chiffrements du même contenu produisent des fichiers différents (IV aléatoire)")
    void encrypt_sameContent_differentCiphertext() throws IOException {
        byte[] original = "même contenu deux fois".getBytes();
        UUID userId = UUID.randomUUID();

        Path temp1 = Files.createTempFile(tempDir, "t1-", ".tmp");
        Path temp2 = Files.createTempFile(tempDir, "t2-", ".tmp");
        Files.write(temp1, original);
        Files.write(temp2, original);

        String path1 = storageService.encryptAndStore(temp1, userId, UUID.randomUUID());
        String path2 = storageService.encryptAndStore(temp2, userId, UUID.randomUUID());

        byte[] cipher1 = Files.readAllBytes(Path.of(path1));
        byte[] cipher2 = Files.readAllBytes(Path.of(path2));

        // Les IV sont différents → les ciphertexts doivent être différents
        assertThat(cipher1).isNotEqualTo(cipher2);
    }

    @Test
    @DisplayName("Altération d'un octet du fichier chiffré lève une exception au déchiffrement")
    void tampered_ciphertext_throwsException() throws IOException {
        byte[] original = "données à protéger".getBytes();
        UUID userId   = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

        Path tempFile = Files.createTempFile(tempDir, "test-", ".tmp");
        Files.write(tempFile, original);

        String storagePath = storageService.encryptAndStore(tempFile, userId, recordId);

        // Altérer un octet du fichier chiffré (après l'IV, dans le ciphertext)
        byte[] corrupted = Files.readAllBytes(Path.of(storagePath));
        corrupted[20] ^= 0xFF;
        Files.write(Path.of(storagePath), corrupted);

        assertThatThrownBy(() -> storageService.decryptToBytes(storagePath))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("corrompu");
    }

    @Test
    @DisplayName("decryptToTempFile retourne un fichier avec le contenu original")
    void decryptToTempFile_returnsCorrectContent() throws IOException {
        byte[] original = "test temp file decrypt".getBytes();
        UUID userId   = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

        Path tempFile = Files.createTempFile(tempDir, "src-", ".tmp");
        Files.write(tempFile, original);

        String storagePath = storageService.encryptAndStore(tempFile, userId, recordId);
        Path decryptedTemp = storageService.decryptToTempFile(storagePath);

        try {
            assertThat(Files.readAllBytes(decryptedTemp)).isEqualTo(original);
        } finally {
            Files.deleteIfExists(decryptedTemp);
        }
    }
}
