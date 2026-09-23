package com.realis.service;

import java.time.Instant;
import com.realis.dto.SealRequest;

public final class ConsentPolicy {
    public static final String VERSION = "2026-09-23";
    public static final int DAYS = 365;
    public static final String TEXT = "Enregistrement d'un état des lieux immobilier à des fins de preuve d'intégrité et d'antériorité. Les captures sont conservées 365 jours à compter du scellement. Une suppression retire immédiatement l'accès public et déclenche l'effacement des données actives sous une heure. Les sauvegardes expirent sous 30 jours. La géolocalisation facultative est déclarative et visible par toute personne disposant du lien du certificat.";
    private ConsentPolicy() {}
    public static void validate(SealRequest request) {
        if (!request.consentAccepted() || !VERSION.equals(request.policyVersion()) || request.consentedAt() == null)
            throw new IllegalArgumentException("Acceptez la politique de conservation en vigueur avant la capture.");
        if (request.consentedAt().isAfter(Instant.now().plusSeconds(300)))
            throw new IllegalArgumentException("Date de consentement future invalide.");
        if ((request.geolocLat() == null) != (request.geolocLng() == null))
            throw new IllegalArgumentException("Coordonnées GPS incomplètes.");
        if (request.geolocLat() != null && (!request.geolocConsented() ||
            !Double.isFinite(request.geolocLat()) || !Double.isFinite(request.geolocLng()) ||
            Math.abs(request.geolocLat()) > 90 || Math.abs(request.geolocLng()) > 180))
            throw new IllegalArgumentException("Coordonnées GPS ou consentement invalides.");
        if (request.captureId() == null) throw new IllegalArgumentException("Identifiant de capture requis.");
        if (request.deviceUa() != null && request.deviceUa().length() > 512)
            throw new IllegalArgumentException("Informations d'appareil trop longues.");
    }
}
