package com.realis.controller;

import com.realis.dto.SealRequest;
import com.realis.dto.SealResponse;
import com.realis.exception.ResourceNotFoundException;
import com.realis.model.SealedRecord;
import com.realis.repository.SealedRecordRepository;
import com.realis.service.PdfCertificateService;
import com.realis.service.SealingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/seal")
@RequiredArgsConstructor
public class SealingController {

    private final SealingService sealingService;
    private final PdfCertificateService pdfCertificateService;
    private final SealedRecordRepository sealedRecordRepository;

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
     * Génère et retourne le certificat PDF à la demande.
     * Accessible sans authentification (lien partageable).
     */
    @GetMapping("/{id}/certificate")
    public ResponseEntity<byte[]> getCertificate(@PathVariable UUID id) throws IOException {
        SealedRecord record = sealedRecordRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Enregistrement introuvable : " + id));

        byte[] pdfBytes = pdfCertificateService.generate(record);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
            ContentDisposition.attachment()
                .filename("certificat-realis-" + id + ".pdf")
                .build()
        );
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    /**
     * Suppression logique — invalide la preuve définitivement.
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
