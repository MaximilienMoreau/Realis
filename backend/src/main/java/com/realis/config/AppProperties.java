package com.realis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * URL publique du frontend (sans slash final), utilisée pour construire des liens
 * absolus dans les artefacts partagés (ex. certificat PDF).
 */
@ConfigurationProperties(prefix = "realis.app")
public record AppProperties(
    String frontendUrl
) {}
