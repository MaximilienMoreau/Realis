package com.realis.repository;

import com.realis.model.SealedRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SealedRecordRepository extends JpaRepository<SealedRecord, UUID> {

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
