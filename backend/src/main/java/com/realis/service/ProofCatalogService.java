package com.realis.service;
import com.realis.dto.SealResponse;
import com.realis.model.*;
import com.realis.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service @RequiredArgsConstructor
public class ProofCatalogService {
    private final SealedRecordRepository records;
    private final ProofLabelRepository labels;
    public record Item(SealResponse record, String title, String folder) {}
    public record Results(java.util.List<Item> content, int totalPages, long totalElements, int number) {}
    @Transactional(readOnly=true)
    public Results list(UUID owner, String q, String folder, int page) {
        if (q.length() > 160 || folder.length() > 160 || page < 0) throw new IllegalArgumentException("Recherche invalide");
        Page<Item> result = records.search(owner, q.toLowerCase(java.util.Locale.ROOT), folder,
            Instant.now().minus(365, ChronoUnit.DAYS), PageRequest.of(page, 20)).map(r -> {
                var l = labels.findById(r.getId()).orElse(new ProofLabel());
                return new Item(SealResponse.from(r), l.getTitle(), l.getFolder());
            });
        return new Results(result.getContent(), result.getTotalPages(), result.getTotalElements(), result.getNumber());
    }
    @Transactional
    public void update(UUID owner, UUID id, String title, String folder) {
        if (title == null || folder == null || title.length() > 160 || folder.length() > 160)
            throw new IllegalArgumentException("Titre et dossier limités à 160 caractères");
        var r = records.findById(id).orElseThrow(() -> new com.realis.exception.ResourceNotFoundException("Preuve introuvable"));
        if (!r.getUser().getId().equals(owner)) throw new SecurityException("Accès refusé");
        VerificationService.requireAvailable(r);
        var label = labels.findById(id).orElse(new ProofLabel());
        label.setRecordId(id); label.setTitle(title.trim()); label.setFolder(folder.trim()); labels.save(label);
    }
}
