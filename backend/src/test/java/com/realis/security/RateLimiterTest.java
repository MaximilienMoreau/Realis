package com.realis.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimiter : fenêtre fixe par minute")
class RateLimiterTest {

    private static final long WINDOW_MS = 60_000;

    @Test
    @DisplayName("Autorise jusqu'à la limite, puis refuse")
    void tryAcquire_allowsUpToLimitThenRejects() {
        RateLimiter limiter = new RateLimiter();
        String key = "login:127.0.0.1";

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(key, 5)).isTrue();
        }
        assertThat(limiter.tryAcquire(key, 5)).isFalse();
    }

    @Test
    @DisplayName("Deux clés distinctes ont des compteurs indépendants")
    void tryAcquire_independentPerKey() {
        RateLimiter limiter = new RateLimiter();

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("login:1.1.1.1", 3)).isTrue();
        }
        assertThat(limiter.tryAcquire("login:1.1.1.1", 3)).isFalse();

        // Une autre IP ne doit pas être affectée par l'épuisement de la première
        assertThat(limiter.tryAcquire("login:2.2.2.2", 3)).isTrue();
    }

    @Test
    @DisplayName("Une nouvelle fenêtre réinitialise le compteur d'une clé")
    void tryAcquire_newWindowResetsCount() {
        RateLimiter limiter = new RateLimiter();
        long t0 = 1_000_000L;

        assertThat(limiter.tryAcquire("login:3.3.3.3", 1, t0)).isTrue();
        assertThat(limiter.tryAcquire("login:3.3.3.3", 1, t0 + 100)).isFalse();

        // Fenêtre suivante : le compteur repart de zéro
        assertThat(limiter.tryAcquire("login:3.3.3.3", 1, t0 + WINDOW_MS + 1)).isTrue();
    }

    @Test
    @DisplayName("Les fenêtres expirées sont purgées de la map interne (pas de fuite mémoire)")
    void cleanup_removesExpiredWindows() {
        RateLimiter limiter = new RateLimiter();
        long t0 = 1_000_000L;

        limiter.tryAcquire("login:9.9.9.9", 10, t0);
        assertThat(limiter.trackedKeyCount()).isEqualTo(1);

        // Une autre clé, longtemps après (> WINDOW_MS) : déclenche le balayage best-effort
        limiter.tryAcquire("login:8.8.8.8", 10, t0 + 2 * WINDOW_MS);

        // L'entrée expirée a été retirée ; seule la clé récente subsiste
        assertThat(limiter.trackedKeyCount()).isEqualTo(1);
    }
}
