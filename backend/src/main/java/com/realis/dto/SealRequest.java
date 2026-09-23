package com.realis.dto;
import java.time.Instant;
import java.util.UUID;
public record SealRequest(String mimeType, Double geolocLat, Double geolocLng, String deviceUa,
    UUID captureId, boolean consentAccepted, boolean geolocConsented,
    String policyVersion, Instant consentedAt) {}
