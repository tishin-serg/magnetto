package ru.xataaa.torrentbot.config;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortcut-api")
public record ShortcutApiProperties(boolean enabled, UUID userId, Long telegramChatId,
                                    String tokenSha256, int requestsPerMinute, int creationsPerMinute,
                                    String publicUrl, long temporaryTtlHours, String temporaryS3Prefix) {
    public ShortcutApiProperties {
        requestsPerMinute = requestsPerMinute <= 0 ? 30 : requestsPerMinute;
        creationsPerMinute = creationsPerMinute <= 0 ? 5 : creationsPerMinute;
        temporaryTtlHours = temporaryTtlHours <= 0 ? 24 : temporaryTtlHours;
        temporaryS3Prefix = temporaryS3Prefix == null || temporaryS3Prefix.isBlank() ? "temporary/" : temporaryS3Prefix;
    }
    public boolean configured() { return enabled && userId != null && tokenSha256 != null && !tokenSha256.isBlank(); }
}
