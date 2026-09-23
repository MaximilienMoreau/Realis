package com.realis.service;

import com.realis.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetentionJob {
    private final SealedRecordRepository records;
    private final UserRepository users;
    private final ProofLifecycleService lifecycle;
    private final JdbcTemplate jdbc;
    @Scheduled(fixedDelayString = "${realis.retention.interval-ms:60000}")
    public void run() {
        for (var id : records.findPurgeCandidates(Instant.now().minus(365, ChronoUnit.DAYS), PageRequest.of(0, 100))) {
            try { lifecycle.purge(id); }
            catch (Exception e) { log.error("Effacement à réessayer pour {}", id, e); }
        }
        for (var user : users.findByDeletedAtIsNotNull()) {
            try { lifecycle.finishAccountDeletion(user.getId()); }
            catch (Exception e) { log.error("Effacement du compte à réessayer", e); }
        }
        jdbc.update("delete from account_tokens where expires_at < now()");
    }
}
