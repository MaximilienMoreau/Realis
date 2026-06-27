package com.realis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "realis.jwt")
public record JwtProperties(
    String secret,
    long expirationMs
) {}
