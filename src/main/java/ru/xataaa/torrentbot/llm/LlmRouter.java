package ru.xataaa.torrentbot.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.SafeLog;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmRouter {

    private static final String PROMPT_RESOURCE = "llm-router-prompt.md";

    private final OllamaChatClient ollamaChatClient;
    private final ObjectMapper objectMapper;
    private final BotScenarioCatalog botScenarioCatalog;

    public LlmRouteResult route(Long userId, String text, UserDialogState state) {
        String safeText = text == null ? "" : text.trim();
        log.info("llm_router_input: userId={}, textPreview={}", userId, SafeLog.preview(safeText, 80));
        try {
            String rawJson = ollamaChatClient.chat(systemPrompt(), userPrompt(safeText, state));
            log.info("llm_router_raw_json: userId={}, jsonPreview={}", userId, SafeLog.preview(rawJson, 400));
            LlmRouteResult routeResult = parseAndNormalize(rawJson);
            log.info("llm_router_action_selected: userId={}, action={}, confidence={}",
                    userId, routeResult.action().code(), routeResult.confidence());
            return routeResult;
        } catch (RuntimeException runtimeException) {
            log.warn("llm_router_failed: userId={}, fallback=show_help, error={}", userId, runtimeException.getMessage());
            return LlmRouteResult.unknown();
        }
    }

    public LlmRouteResult parseAndNormalize(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(rawJson));
            requireFields(root, "action", "confidence", "arguments", "reply");
            rejectUnknownFields(root, Set.of("action", "confidence", "arguments", "reply"));
            LlmAction action = LlmAction.fromCode(textValue(root, "action"));
            double confidence = clamp(root.path("confidence").asDouble(0.0));
            Map<String, Object> arguments = normalizeArguments(root.path("arguments"));
            String reply = nullIfBlank(textValue(root, "reply"));
            return new LlmRouteResult(action, confidence, arguments, reply);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.warn("llm_router_json_parse_failed: fallback=show_help, error={}", exception.getMessage());
            return LlmRouteResult.unknown();
        }
    }

    public UserDialogState updateState(Long userId, LlmRouteResult routeResult) {
        Map<String, Object> lastSearch = routeResult.action() == LlmAction.SEARCH_MEDIA
                ? routeResult.arguments()
                : Map.of();
        return new UserDialogState(
                userId,
                routeResult.action().code(),
                lastSearch,
                Map.of(),
                Map.of(),
                Instant.now()
        );
    }

    public boolean requiresFallback(LlmRouteResult routeResult, double minConfidence) {
        return routeResult == null
                || routeResult.action() == LlmAction.UNKNOWN
                || routeResult.confidence() < minConfidence;
    }

    public boolean hasSearchTitle(LlmRouteResult routeResult) {
        if (routeResult == null || routeResult.arguments() == null) {
            return false;
        }
        Object title = routeResult.arguments().get("title");
        return title != null && !title.toString().isBlank();
    }

    private Map<String, Object> normalizeArguments(JsonNode argumentsNode) {
        requireFields(
                argumentsNode,
                "title",
                "media_type",
                "season",
                "episodes",
                "quality",
                "source",
                "audio",
                "language",
                "destination",
                "task_id"
        );
        rejectUnknownFields(argumentsNode, Set.of(
                "title",
                "media_type",
                "season",
                "episodes",
                "quality",
                "source",
                "audio",
                "language",
                "destination",
                "task_id"
        ));
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("title", normalizeTitle(textValue(argumentsNode, "title")));
        arguments.put("media_type", normalizeMediaType(textValue(argumentsNode, "media_type")));
        arguments.put("season", normalizeInteger(argumentsNode.get("season")));
        arguments.put("episodes", nullIfBlank(textValue(argumentsNode, "episodes")));
        arguments.put("quality", normalizeQuality(textValue(argumentsNode, "quality")));
        arguments.put("source", normalizeSource(textValue(argumentsNode, "source")));
        arguments.put("audio", normalizeAudio(textValue(argumentsNode, "audio")));
        arguments.put("language", nullIfBlank(textValue(argumentsNode, "language")));
        arguments.put("destination", normalizeDestination(textValue(argumentsNode, "destination")));
        arguments.put("task_id", nullIfBlank(textValue(argumentsNode, "task_id")));
        return arguments;
    }

    private String systemPrompt() {
        try {
            byte[] bytes = new ClassPathResource(PROMPT_RESOURCE).getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8) + "\n\n" + botScenarioCatalog.promptText();
        } catch (IOException exception) {
            log.warn("llm_router_prompt_load_failed: resource={}, error={}", PROMPT_RESOURCE, exception.getMessage());
            return botScenarioCatalog.promptText();
        }
    }

    private String userPrompt(String text, UserDialogState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", text);
        payload.put("state", state == null ? Map.of() : statePayload(state));
        payload.put("available_actions", botScenarioCatalog.actions());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{\"text\":\"" + text.replace("\"", "\\\"") + "\"}";
        }
    }

    private Map<String, Object> statePayload(UserDialogState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", state.userId());
        payload.put("current_flow", state.currentFlow());
        payload.put("last_search", state.lastSearch());
        payload.put("last_results", state.lastResults());
        payload.put("selected_result", state.selectedResult());
        payload.put("updated_at", state.updatedAt() == null ? null : state.updatedAt().toString());
        return payload;
    }

    private String extractJson(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("empty LLM response");
        }
        String trimmed = value.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("LLM response does not contain JSON object");
        }
        return trimmed.substring(start, end + 1);
    }

    private String textValue(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private String normalizeTitle(String title) {
        String value = nullIfBlank(title);
        return value == null ? null : value.trim();
    }

    private String normalizeMediaType(String mediaType) {
        String value = nullIfBlank(mediaType);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("movie".equals(normalized) || "series".equals(normalized)) {
            return normalized;
        }
        if ("tv".equals(normalized) || "serial".equals(normalized) || "сериал".equals(normalized)) {
            return "series";
        }
        if ("film".equals(normalized) || "фильм".equals(normalized)) {
            return "movie";
        }
        return null;
    }

    private Integer normalizeInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.canConvertToInt()) {
            return node.asInt();
        }
        try {
            return Integer.parseInt(node.asText().trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeQuality(String quality) {
        String value = nullIfBlank(quality);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.contains("2160") || normalized.equals("4k") || normalized.equals("4к") || normalized.equals("uhd")) {
            return "2160p";
        }
        if (normalized.contains("1080") || normalized.equals("fhd") || normalized.equals("fullhd")) {
            return "1080p";
        }
        if (normalized.contains("720")) {
            return "720p";
        }
        return null;
    }

    private String normalizeSource(String source) {
        String value = nullIfBlank(source);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "");
        if (normalized.contains("bdremux") || normalized.equals("remux") || normalized.equals("ремукс")) {
            return "BDRemux";
        }
        if (normalized.contains("webdl")) {
            return "WEB-DL";
        }
        if (normalized.contains("bdrip")) {
            return "BDRip";
        }
        return null;
    }

    private String normalizeAudio(String audio) {
        String value = nullIfBlank(audio);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("дуб") || normalized.equals("дб") || normalized.equals("dubbed")) {
            return "Дубляж";
        }
        if (normalized.contains("многоголос") || normalized.equals("мво")) {
            return "Многоголосый";
        }
        if (normalized.contains("original") || normalized.contains("оригинал")) {
            return "Original";
        }
        return null;
    }

    private String normalizeDestination(String destination) {
        String value = nullIfBlank(destination);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        if ("home_pc".equals(normalized) || "home".equals(normalized) || "домой".equals(normalized)) {
            return "home_pc";
        }
        if ("vps".equals(normalized) || "server".equals(normalized) || "сервер".equals(normalized)) {
            return "vps";
        }
        if ("s3".equals(normalized) || "cloud".equals(normalized) || "облако".equals(normalized)) {
            return "s3";
        }
        return null;
    }

    private void requireFields(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || !node.isObject()) {
            throw new IllegalArgumentException("required JSON object is missing");
        }
        for (String field : fields) {
            if (!node.has(field)) {
                throw new IllegalArgumentException("required field is missing: " + field);
            }
        }
    }

    private void rejectUnknownFields(JsonNode node, Set<String> allowedFields) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowedFields.contains(field)) {
                throw new IllegalArgumentException("unknown field is not allowed: " + field);
            }
        });
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim()) ? null : value.trim();
    }

    private double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
