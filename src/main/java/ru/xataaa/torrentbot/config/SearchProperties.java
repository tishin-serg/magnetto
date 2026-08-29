package ru.xataaa.torrentbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public record SearchProperties(
        int inlineCacheSeconds,
        int inlineDebounceMs,
        String defaultLanguage,
        int sessionTtlMinutes,
        String defaultQuality,
        String defaultVoice
) {
}
