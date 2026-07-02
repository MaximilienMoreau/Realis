package com.realis.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class HashService {

    private static final int BUFFER_SIZE = 65_536;

    /**
     * Calcule le SHA-256 d'un flux entrant et copie simultanément les octets vers dest.
     * Une seule lecture du flux : pas de chargement en mémoire.
     *
     * Le hash porte sur les octets bruts tels qu'ils arrivent, sans aucun ré-encodage.
     *
     * @param input flux source (vidéo uploadée)
     * @param dest  destination pour la copie (fichier temporaire avant chiffrement)
     * @return hash SHA-256 encodé en hexadécimal minuscule
     */
    public String sha256AndCopy(InputStream input, OutputStream dest) throws IOException {
        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
            dest.write(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Calcule le SHA-256 d'un flux sans copier les octets (utilisé pour la vérification).
     * Aucune écriture disque : uniquement lecture + hash.
     */
    public String sha256Hex(InputStream input) throws IOException {
        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Calcule le SHA-256 d'un tableau d'octets (utilitaire pour les tests et la vérification).
     */
    public String sha256Hex(byte[] data) {
        return HexFormat.of().formatHex(newSha256Digest().digest(data));
    }

    /**
     * Décode un hash hexadécimal en tableau d'octets (pour la soumission à la TSA).
     */
    public byte[] hexToBytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    private MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti présent dans toute JVM compatible Java 7+
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }
}
