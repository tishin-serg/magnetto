package ru.xataaa.torrentbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tmdb")
public record TmdbProperties(
        String apiKey,
        String baseUrl,
        String imageBaseUrl,
        String language,
        int connectTimeoutMs,
        int requestTimeoutMs,
        int cacheTtlMinutes,
        int detailsCacheTtlMinutes,
        int maxInlineResults
) {
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
