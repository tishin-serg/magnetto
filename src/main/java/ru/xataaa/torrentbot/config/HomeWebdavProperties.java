package ru.xataaa.torrentbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.home-webdav")
public record HomeWebdavProperties(
        boolean enabled,
        String baseUrl,
        String localBaseUrl,
        String username,
        String password,
        int connectTimeoutMs,
        int requestTimeoutMs
) {
}
