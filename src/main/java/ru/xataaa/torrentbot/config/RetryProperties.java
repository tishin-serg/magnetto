package ru.xataaa.torrentbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "retry")
public record RetryProperties(
        int maxAttempts,
        long initialDelayMs,
        long secondDelayMs,
        long maxDelayMs
) {
}
