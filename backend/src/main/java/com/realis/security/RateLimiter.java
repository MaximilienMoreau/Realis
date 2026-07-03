package com.realis.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limiteur de débit en mémoire, fenêtre fixe glissante par minute.
 * Suffisant pour une instance unique (MVP) ; à remplacer par un backend partagé
 * (ex. Redis) si le backend est un jour répliqué.
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MS = 60_000;

    private record Window(long windowStart, AtomicInteger count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * @return true si l'appel est autorisé (sous la limite), false si la limite est dépassée.
     */
    public boolean tryAcquire(String key, int maxAttemptsPerMinute) {
        long now = Instant.now().toEpochMilli();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart() >= WINDOW_MS) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return window.count().get() <= maxAttemptsPerMinute;
    }
}
