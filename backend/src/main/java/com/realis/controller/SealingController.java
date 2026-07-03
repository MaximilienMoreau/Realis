package com.realis.controller;

import com.realis.dto.SealRequest;
import com.realis.dto.SealResponse;
import com.realis.service.SealingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/seal")
@RequiredArgsConstructor
public class SealingController {

    private final SealingService sealingService;

    /**
     * Scelle une capture :
     *   POST /api/seal  (multipart/form-data)
     *   Champs : file (requis), mimeType, geolocLat, geolocLng, deviceUa
     *   L'identité de l'utilisateur est extraite du JWT (Authorization: Bearer ...).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SealResponse> seal(
        @RequestPart("file") MultipartFile file,
        @RequestParam(value = "mimeType",  required = false) String  mimeType,
        @RequestParam(value = "geolocLat", required = false) Double  geolocLat,
        @RequestParam(value = "geolocLng", required = false) Double  geolocLng,
        @RequestParam(value = "deviceUa",  required = false) String  deviceUa,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier ne peut pas être vide");
        }
        UUID userId = (UUID) authentication.getPrincipal();
        SealRequest request = new SealRequest(mimeType, geolocLat, geolocLng, deviceUa);
        SealResponse response = sealingService.seal(file, userId, request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SealResponse> getById(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(sealingService.findByIdForOwner(id, userId));
    }

    /**
     * Liste les scellements actifs de l'utilisateur connecté (le plus récent en premier).
     */
    @GetMapping
    public ResponseEntity<List<SealResponse>> list(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(sealingService.listForOwner(userId));
    }

    /**
     * Suppression logique : invalide la preuve définitivement.
     * L'identité du demandeur est vérifiée via JWT (doit correspondre au propriétaire).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<SealResponse> softDelete(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        UUID userId = (UUID) authentication.getPrincipal();
        SealResponse response = sealingService.softDelete(id, userId);
        return ResponseEntity.ok(response);
    }
}
