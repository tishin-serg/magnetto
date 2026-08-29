package ru.xataaa.torrentbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "downloads")
public record DownloadsProperties(
        String publicBaseUrl,
        String internalNginxLocationPrefix,
        String storagePath,
        long cleanupIntervalMs
) {
}
