package ru.xataaa.torrentbot.llm;

import java.util.Map;

public record LlmRouteResult(
        LlmAction action,
        double confidence,
        Map<String, Object> arguments,
        String reply
) {
    public static LlmRouteResult unknown() {
        return new LlmRouteResult(LlmAction.UNKNOWN, 0.0, Map.of(), null);
    }
}
