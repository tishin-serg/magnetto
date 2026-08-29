package ru.xataaa.torrentbot.llm;

import java.time.Instant;
import java.util.Map;

public record UserDialogState(
        Long userId,
        String currentFlow,
        Map<String, Object> lastSearch,
        Map<String, Object> lastResults,
        Map<String, Object> selectedResult,
        Instant updatedAt
) {
    public static UserDialogState empty(Long userId) {
        return new UserDialogState(userId, null, Map.of(), Map.of(), Map.of(), Instant.now());
    }
}
