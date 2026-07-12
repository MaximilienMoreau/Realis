package com.realis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Contrôle si Realis doit faire confiance à l'en-tête X-Forwarded-For pour déterminer
 * l'IP cliente (rate-limiting, journal de consentement), plutôt qu'à la connexion TCP
 * directe.
 *
 * Ne JAMAIS activer sans un reverse proxy de confiance placé devant l'application et
 * configuré pour écraser tout X-Forwarded-For entrant (plutôt que de l'accumuler) :
 * sinon n'importe quel client peut usurper une IP arbitraire via cet en-tête et
 * contourner entièrement la limitation de débit.
 */
@ConfigurationProperties(prefix = "realis.network")
public record NetworkProperties(
    @DefaultValue("false") boolean trustForwardedFor
) {}
