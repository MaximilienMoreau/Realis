package com.realis.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

// Enregistrement scellé — tous les champs de preuve sont updatable=false.
// Seul deletedAt peut être posé (suppression logique via endpoint dédié).
// Un trigger PostgreSQL renforce cette immuabilité côté DB.
@Entity
@Table(name = "sealed_records")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SealedRecord {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_log_id", nullable = false, updatable = false)
    private ConsentLog consentLog;

    @Column(nullable = false, updatable = false)
    private String fileName;

    @Column(nullable = false, updatable = false)
    private long fileSizeBytes;

    @Column(nullable = false, updatable = false)
    private String mimeType;

    // SHA-256 des octets bruts du fichier tel qu'uploadé (pas de ré-encodage)
    @Column(name = "sha256_hex", nullable = false, updatable = false)
    private String sha256Hex;

    // Jeton RFC 3161 brut (DER) — vérifiable indépendamment de Realis
    @Column(nullable = false, updatable = false, columnDefinition = "BYTEA")
    private byte[] tsaTokenDer;

    @Column(nullable = false, updatable = false)
    private String tsaUrl;

    @Column(nullable = false, updatable = false)
    private Instant tsaTimestamp;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant sealedAt = Instant.now();

    @Column(updatable = false)
    private Double geolocLat;

    @Column(updatable = false)
    private Double geolocLng;

    @Column(updatable = false)
    private String deviceUa;

    // Chemin vers le fichier chiffré sur volume
    @Column(nullable = false, updatable = false)
    private String storagePath;

    // Suppression logique uniquement (casse la preuve — avertissement obligatoire)
    @Setter
    private Instant deletedAt;
}
