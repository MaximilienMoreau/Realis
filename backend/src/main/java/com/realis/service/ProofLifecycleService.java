package com.realis.service;

import com.realis.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.UUID;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class ProofLifecycleService {
    private final SealedRecordRepository records;
    private final UserRepository users;
    private final StorageService storage;
    private final JdbcTemplate jdbc;

    @Transactional(rollbackFor = Exception.class)
    public void purge(UUID id) throws IOException {
        var candidate = records.findById(id);
        if (candidate.isEmpty()) return;
        var r = candidate.get();
        // Same per-user lock as sealing and account deletion.
        users.lockById(r.getUser().getId()).orElseThrow();
        if (r.isAvailable() && r.getUser().getDeletedAt() == null) return;
        if (r.getDeletedAt() == null) {
            r.setDeletedAt(Instant.now());
            records.saveAndFlush(r);
        }
        storage.delete(r.getStoragePath()); // idempotent; on failure retain row for retry
        UUID consentId = r.getConsentLog().getId();
        records.delete(r);
        records.flush();
        jdbc.update("delete from consent_logs where id = ?", consentId);
    }

    @Transactional
    public void finishAccountDeletion(UUID id) {
        var user = users.lockById(id).orElse(null);
        if (user != null && user.getDeletedAt() != null && records.findByUserId(id).isEmpty()) {
            jdbc.update("delete from consent_logs where user_id = ?", id);
            users.delete(user);
        }
    }
}
