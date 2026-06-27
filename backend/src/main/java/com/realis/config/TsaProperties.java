package com.realis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "realis.tsa")
public record TsaProperties(
    String provider,
    String url,
    String certPath,
    int timeoutMs
) {}
