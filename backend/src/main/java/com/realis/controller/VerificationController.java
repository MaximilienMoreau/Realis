package com.realis.controller;

import com.realis.dto.SealResponse;
import com.realis.dto.VerificationResponse;
import com.realis.exception.ResourceNotFoundException;
import com.realis.model.SealedRecord;
import com.realis.repository.SealedRecordRepository;
import com.realis.service.PdfCertificateService;
import com.realis.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Endpoints de vérification : tous publics, sans authentification.
 *
 * POST /api/verify                → verdict sur un fichier uploadé
 * GET  /api/verify/{id}           → métadonnées publiques d'un enregistrement
 * GET  /api/verify/{id}/tsa       → jeton TSA brut (DER) pour vérification indépendante
 * GET  /api/verify/{id}/certificate → certificat PDF (lien partageable)
 */
@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;
    private final PdfCertificateService pdfCertificateService;
    private final SealedRecordRepository sealedRecordRepository;

    /**
     * Vérifie l'intégrité et l'horodatage d'un fichier.
     *
     * Paramètres :
     *  - file     : le fichier à vérifier (obligatoire)
     *  - recordId : UUID de l'enregistrement de référence (optionnel)
     *               → sans recordId : verdict AUTHENTIQUE ou INCONNU
     *               → avec recordId : verdict AUTHENTIQUE ou ALTÉRÉ
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResponse> verify(
        @RequestPart("file") MultipartFile file,
        @RequestParam(value = "recordId", required = false) UUID recordId
    ) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier ne peut pas être vide");
        }

        VerificationResponse response = verificationService.verify(file, recordId);

        HttpStatus status = switch (response.verdict()) {
            case AUTHENTIQUE -> HttpStatus.OK;
            case ALTERE      -> HttpStatus.OK;   // 200 avec verdict ALTERE dans le corps
            case INCONNU     -> HttpStatus.NOT_FOUND;
        };

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Retourne les métadonnées publiques d'un enregistrement scellé.
     * Utile pour afficher les détails d'un certificat depuis son ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SealResponse> getPublicMetadata(@PathVariable UUID id) {
        return ResponseEntity.ok(verificationService.getPublicMetadata(id));
    }

    /**
     * Exporte le jeton TSA brut (DER, extension .tsr) d'un enregistrement.
     *
     * Permet une vérification cryptographique totalement indépendante de Realis :
     *   openssl ts -verify -in token.tsr -data fichier.webm -CAfile freetsa-ca.crt
     */
    @GetMapping("/{id}/tsa")
    public ResponseEntity<byte[]> getTsaToken(@PathVariable UUID id) {
        byte[] tokenDer = verificationService.getTsaToken(id);

        if (tokenDer.length == 0) {
            throw new ResourceNotFoundException(
                "Aucun jeton TSA disponible pour cet enregistrement (horodatage no-op)"
            );
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(
            ContentDisposition.attachment()
                .filename("realis-tsa-" + id + ".tsr")
                .build()
        );
        headers.setContentLength(tokenDer.length);

        return new ResponseEntity<>(tokenDer, headers, HttpStatus.OK);
    }

    /**
     * Génère et retourne le certificat PDF à la demande (non stocké, toujours frais depuis la DB).
     * Public : c'est un lien partageable (aucun header Authorization possible sur un <a href download>).
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
}
