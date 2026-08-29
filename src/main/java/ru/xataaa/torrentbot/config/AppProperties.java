package ru.xataaa.torrentbot.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        long maxFileSizeBytes,
        long progressPollIntervalMs,
        long recoveryPollIntervalMs,
        String allowedChatIds,
        boolean deleteAfterSuccessfulUpload,
        long metadataTimeoutMinutes,
        long downloadTimeoutHours,
        long uploadTimeoutHours,
        long cleanupTimeoutMinutes,
        long maxJobAgeHours
) {
    public boolean isChatAllowed(Long chatId) {
        Set<Long> configuredChatIds = allowedChatIdsSet();
        return configuredChatIds.isEmpty() || configuredChatIds.contains(chatId);
    }

    public Set<Long> allowedChatIdsSet() {
        if (allowedChatIds == null || allowedChatIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedChatIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }
}
