package com.realis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "realis.storage")
public record StorageProperties(
    String path,
    String encryptionKey
) {}
