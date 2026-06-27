package com.realis.repository;

import com.realis.model.SealedRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SealedRecordRepository extends JpaRepository<SealedRecord, UUID> {

    // Recherche par hash (pour la page de vérification publique)
    @Query("SELECT r FROM SealedRecord r WHERE r.sha256Hex = :sha256Hex AND r.deletedAt IS NULL")
    Optional<SealedRecord> findActiveBySha256Hex(String sha256Hex);

    @Query("SELECT r FROM SealedRecord r WHERE r.user.id = :userId AND r.deletedAt IS NULL ORDER BY r.sealedAt DESC")
    List<SealedRecord> findActiveByUserId(UUID userId);
}
