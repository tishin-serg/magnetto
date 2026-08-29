package ru.xataaa.torrentbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jacred")
public record JacredProperties(
        String baseUrl,
        String apiKey,
        int connectTimeoutMs,
        int requestTimeoutMs,
        int maxResults
) {
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
