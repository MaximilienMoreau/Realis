package com.realis.dto;

import com.realis.model.SealedRecord;

import java.time.Instant;
import java.util.UUID;

public record SealResponse(
    UUID    id,
    String  sha256Hex,
    String  fileName,
    long    fileSizeBytes,
    String  mimeType,
    Instant sealedAt,
    Instant tsaTimestamp,
    String  tsaUrl,
    boolean tsaActive,          // false tant que no-op ; true dès FreeTSA branché
    Double  geolocLat,
    Double  geolocLng,
    boolean deleted,
    String  warning             // non-null si l'enregistrement est supprimé
) {

    public static SealResponse from(SealedRecord r) {
        boolean deleted = r.getDeletedAt() != null;
        return new SealResponse(
            r.getId(),
            r.getSha256Hex(),
            r.getFileName(),
            r.getFileSizeBytes(),
            r.getMimeType(),
            r.getSealedAt(),
            r.getTsaTimestamp(),
            r.getTsaUrl(),
            !r.getTsaUrl().startsWith("no-op://"),
            r.getGeolocLat(),
            r.getGeolocLng(),
            deleted,
            deleted
                ? "AVERTISSEMENT : cet enregistrement a été supprimé le " + r.getDeletedAt()
                  + ". La preuve d'intégrité ne peut plus être garantie."
                : null
        );
    }
}
