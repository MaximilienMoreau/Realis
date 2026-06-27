package com.realis.dto;

import java.util.UUID;

/**
 * Paramètres de la requête de scellement (form-data multipart).
 * Le fichier est passé séparément via MultipartFile.
 * L'identité de l'utilisateur est extraite du JWT par le contrôleur.
 */
public record SealRequest(
    String  mimeType,
    Double  geolocLat,
    Double  geolocLng,
    String  deviceUa
) {}
