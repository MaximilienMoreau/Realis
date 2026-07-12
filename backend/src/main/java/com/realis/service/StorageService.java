package com.realis.service;

import com.realis.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Chiffrement AES-256-GCM des captures et stockage sur volume.
 *
 * Format du fichier .enc sur disque :
 *   [IV : 12 octets][ciphertext + GCM tag (16 octets en fin de stream)]
 *
 * La clé AES est injectée via variable d'environnement (Base64, 32 octets).
 * Elle n'est jamais stockée en clair dans le dépôt.
 *
 * Note MVP : une seule clé pour tous les fichiers. En production, prévoir
 * une clé dérivée par enregistrement (ex. HKDF) pour limiter l'impact d'une compromission.
 */
@Service
public class StorageService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH  = 12;
    private static final int GCM_TAG_BITS   = 128;
    private static final int BUFFER_SIZE    = 65_536;

    private final SecretKey aesKey;
    private final Path rootPath;
    private final SecureRandom rng = new SecureRandom();

    public StorageService(StorageProperties props) {
        byte[] keyBytes = Base64.getDecoder().decode(props.encryptionKey());
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                "ENCRYPTION_KEY doit être 32 octets encodés en Base64 (trouvé : " + keyBytes.length + " octets)"
            );
        }
        this.aesKey   = new SecretKeySpec(keyBytes, "AES");
        this.rootPath = Path.of(props.path());
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(rootPath);
    }

    /**
     * Chiffre le fichier temporaire et l'écrit dans le volume de stockage.
     *
     * @param tempFile  fichier brut à chiffrer (sera supprimé par l'appelant)
     * @param userId    identifiant de l'utilisateur (structure du répertoire)
     * @param recordId  identifiant de l'enregistrement (nom du fichier)
     * @return chemin absolu du fichier chiffré stocké
     */
    public String encryptAndStore(Path tempFile, UUID userId, UUID recordId) throws IOException {
        byte[] iv = generateIv();

        Path destDir  = rootPath.resolve(userId.toString());
        Files.createDirectories(destDir);
        Path destFile = destDir.resolve(recordId + ".enc");

        Cipher cipher = initCipher(Cipher.ENCRYPT_MODE, iv);

        try (InputStream  in      = Files.newInputStream(tempFile);
             OutputStream rawOut  = Files.newOutputStream(destFile)) {

            rawOut.write(iv);

            try (CipherOutputStream cipherOut = new CipherOutputStream(rawOut, cipher)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    cipherOut.write(buffer, 0, read);
                }
            }
        }

        return destFile.toAbsolutePath().toString();
    }

    /**
     * Déchiffre et retourne les octets d'un enregistrement stocké.
     *
     * Non appelé par le flux de vérification actuel (celui-ci compare uniquement le hash
     * re-calculé du fichier soumis au hash stocké en base, sans jamais relire le volume) :
     * cette méthode existe comme contrepartie symétrique de encryptAndStore, pour des
     * besoins futurs (récupération, contrôle d'intégrité du volume de stockage lui-même).
     */
    public byte[] decryptToBytes(String storagePath) throws IOException {
        byte[] raw = Files.readAllBytes(Path.of(storagePath));

        byte[] iv         = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[raw.length - GCM_IV_LENGTH];
        System.arraycopy(raw, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(raw, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = initCipher(Cipher.DECRYPT_MODE, iv);
        try {
            return cipher.doFinal(ciphertext);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new IOException("Échec du déchiffrement : fichier corrompu ou clé incorrecte", e);
        }
    }

    /**
     * Déchiffre vers un fichier temporaire (pour un futur re-calcul de hash en streaming,
     * non utilisé par le flux de vérification actuel, voir decryptToBytes).
     * L'appelant est responsable de supprimer le fichier temporaire.
     */
    public Path decryptToTempFile(String storagePath) throws IOException {
        Path temp = Files.createTempFile("realis-verify-", ".tmp");
        byte[] plain = decryptToBytes(storagePath);
        Files.write(temp, plain);
        return temp;
    }

    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        rng.nextBytes(iv);
        return iv;
    }

    private Cipher initCipher(int mode, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(mode, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                 | InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Erreur d'initialisation du cipher AES-GCM", e);
        }
    }
}
