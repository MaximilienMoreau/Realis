package com.realis.repository;

import com.realis.model.SealedRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SealedRecordRepository extends JpaRepository<SealedRecord, UUID> {
    @Query("select coalesce(sum(r.fileSizeBytes), 0) from SealedRecord r where r.user.id = :userId")
    long storageUsed(UUID userId);
    List<SealedRecord> findByUserId(UUID userId);
    @Query("select r.id from SealedRecord r where r.deletedAt is not null or r.sealedAt <= :cutoff or r.user.deletedAt is not null")
    List<UUID> findPurgeCandidates(java.time.Instant cutoff, org.springframework.data.domain.Pageable page);


    @Query(value = "select exists(select 1 from deleted_proofs where id = :id)", nativeQuery = true)
    boolean wasDeleted(UUID id);

    @Query("select r from SealedRecord r left join ProofLabel l on l.recordId = r.id " +
        "where r.user.id = :owner and r.deletedAt is null and r.sealedAt > :cutoff " +
        "and (:folder = '' or l.folder = :folder) " +
        "and (:q = '' or locate(:q, lower(r.fileName)) > 0 or locate(:q, lower(l.title)) > 0 or locate(:q, lower(l.folder)) > 0) " +
        "order by r.sealedAt desc, r.id")
    org.springframework.data.domain.Page<SealedRecord> search(UUID owner, String q, String folder,
        java.time.Instant cutoff, org.springframework.data.domain.Pageable page);

    // Recherche par hash (pour la page de vérification publique).
    // Le hash n'est pas garanti unique (deux scellements peuvent porter sur un contenu
    // identique) : on retourne toutes les correspondances actives, la plus récente en tête,
    // plutôt qu'un Optional qui planterait (IncorrectResultSizeDataAccessException) dès
    // qu'un même contenu est scellé deux fois.
    @Query("SELECT r FROM SealedRecord r WHERE r.sha256Hex = :sha256Hex AND r.deletedAt IS NULL ORDER BY r.sealedAt DESC")
    List<SealedRecord> findActiveBySha256Hex(String sha256Hex);

    @Query("SELECT r FROM SealedRecord r WHERE r.user.id = :userId AND r.deletedAt IS NULL ORDER BY r.sealedAt DESC")
    List<SealedRecord> findActiveByUserId(UUID userId);
}
