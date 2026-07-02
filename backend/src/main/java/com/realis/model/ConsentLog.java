package com.realis.model;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// Enregistrement immuable : pas d'UPDATE autorisé (contrainte DB + updatable=false)
@Entity
@Table(name = "consent_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private String sessionId;

    @Column(nullable = false, updatable = false)
    private boolean geolocConsented;

    // Texte exact affiché à l'utilisateur au moment du consentement (auditabilité RGPD)
    @Column(nullable = false, updatable = false)
    private String purposeText;

    @Column(nullable = false, updatable = false)
    private int retentionDays;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant consentedAt = Instant.now();

    @Column(updatable = false)
    private String userAgent;

    @Column(updatable = false)
    private String ipAddress;
}
