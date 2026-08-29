package ru.xataaa.torrentbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        String botToken,
        String botUsername,
        String baseUrl,
        int connectTimeoutMs,
        int requestTimeoutMs,
        int uploadTimeoutMs,
        FileProperties file
) {
    public String botApiBaseUrl() {
        return baseUrl + "/bot" + botToken;
    }

    public record FileProperties(
            long directSendLimitBytes,
            long downloadLinkTtlHours,
            boolean preferDocumentForVideo
    ) {
    }
}
