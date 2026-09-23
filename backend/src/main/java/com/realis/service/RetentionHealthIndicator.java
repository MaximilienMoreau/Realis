package com.realis.service;
import org.springframework.boot.actuate.health.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor
public class RetentionHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbc;
    @Override public Health health() {
        try {
            Boolean overdue = jdbc.queryForObject("select exists(select 1 from sealed_records where deleted_at < now() - interval '1 hour' or sealed_at < now() - interval '365 days 1 hour')", Boolean.class);
            return Boolean.TRUE.equals(overdue) ? Health.down().withDetail("reason", "Erasure backlog exceeds one hour").build() : Health.up().build();
        } catch (Exception e) { return Health.down().build(); }
    }
}
