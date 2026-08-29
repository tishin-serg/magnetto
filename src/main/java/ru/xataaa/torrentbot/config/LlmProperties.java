package ru.xataaa.torrentbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        boolean enabled,
        String ollamaUrl,
        String ollamaModel,
        int timeoutSeconds,
        double minConfidence
) {
}
