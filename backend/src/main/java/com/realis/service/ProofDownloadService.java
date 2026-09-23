package com.realis.service;

import com.realis.repository.SealedRecordRepository;
import com.realis.exception.ResourceNotFoundException;
import com.realis.config.TsaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.zip.*;

@Service
@RequiredArgsConstructor
public class ProofDownloadService {
    private final SealedRecordRepository records;
    private final StorageService storage;
    private final PdfCertificateService pdf;
    private final HashService hashes;
    private final TsaProperties tsa;

    @Transactional(readOnly = true)
    public Path prepare(UUID id, UUID owner, boolean archive) throws IOException {
        var r = records.findById(id).orElseThrow(() -> new ResourceNotFoundException("Preuve introuvable"));
        if (!r.getUser().getId().equals(owner)) throw new SecurityException("Accès refusé");
        VerificationService.requireAvailable(r);
        Path original = storage.decryptToTempFile(r.getStoragePath());
        try {
            try (InputStream in = Files.newInputStream(original)) {
                if (!hashes.sha256Hex(in).equals(r.getSha256Hex())) throw new IOException("Empreinte du stockage invalide");
            }
            if (!archive) return original;
            Path zip = Files.createTempFile("realis-export-", ".zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new ZipEntry("original.bin"));
                Files.copy(original, out); out.closeEntry();
                entry(out, "certificat.pdf", pdf.generate(r));
                entry(out, "token.tsr", r.getTsaTokenDer());
                entry(out, "sha256.txt", (r.getSha256Hex() + "  original.bin\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (r.getTsaTokenDer().length > 0) entry(out, "tsa-ca.crt", Files.readAllBytes(Path.of(tsa.certPath())));
                entry(out, "LISEZ-MOI.txt", ("Original : " + r.getFileName() + "\nFormat : " + r.getMimeType() +
                    "\nVérification du fichier : sha256sum -c sha256.txt\n" +
                    "Vérification TSA : openssl ts -verify -token_in -in token.tsr -data original.bin -CAfile tsa-ca.crt\n" +
                    "Contrôlez indépendamment la provenance du certificat TSA. GPS/appareil sont déclaratifs.\n" +
                    "Si token.tsr est vide, aucun horodatage certifié n'est disponible (développement).\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) { Files.deleteIfExists(zip); throw e; }
            Files.deleteIfExists(original);
            return zip;
        } catch (Exception e) { Files.deleteIfExists(original); throw e; }
    }
    private void entry(ZipOutputStream out, String name, byte[] bytes) throws IOException {
        out.putNextEntry(new ZipEntry(name)); out.write(bytes); out.closeEntry();
    }
}
