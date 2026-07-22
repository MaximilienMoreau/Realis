package com.realis.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limiteur de débit en mémoire, fenêtre fixe par minute (pas glissante : un client peut
 * en théorie émettre jusqu'à 2× maxAttemptsPerMinute en rafale à cheval sur une frontière
 * de fenêtre). Suffisant pour une instance unique (MVP) ; à remplacer par un backend
 * partagé (ex. Redis) si le backend est un jour répliqué.
 *
 * Une clé est créée par (scope, IP) rencontrée. Sans purge, cette map grossirait
 * indéfiniment (une IP différente = une entrée qui ne disparaît jamais). Un balayage
 * best-effort, au plus une fois par fenêtre, retire les entrées expirées.
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MS = 60_000;

    private record Window(long windowStart, AtomicInteger count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(0);

    /**
     * @return true si l'appel est autorisé (sous la limite), false si la limite est dépassée.
     */
    public boolean tryAcquire(String key, int maxAttemptsPerMinute) {
        return tryAcquire(key, maxAttemptsPerMinute, Instant.now().toEpochMilli());
    }

    /** Visibilité package : permet aux tests d'injecter un instant contrôlé (fenêtres, purge). */
    boolean tryAcquire(String key, int maxAttemptsPerMinute, long now) {
        cleanupIfDue(now);
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart() >= WINDOW_MS) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return window.count().get() <= maxAttemptsPerMinute;
    }

    /** Visibilité package : nombre de clés actuellement suivies (observabilité pour les tests). */
    int trackedKeyCount() {
        return windows.size();
    }

    /** Purge les fenêtres expirées, au plus une fois toutes les WINDOW_MS. */
    private void cleanupIfDue(long now) {
        long last = lastCleanup.get();
        if (now - last < WINDOW_MS) return;
        if (lastCleanup.compareAndSet(last, now)) {
            windows.entrySet().removeIf(e -> now - e.getValue().windowStart() >= WINDOW_MS);
        }
    }
}
