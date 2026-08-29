package ru.xataaa.torrentbot.llm;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LlmRouterJsonSchema {

    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("action", "confidence", "arguments", "reply"),
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "search_media",
                                        "show_tasks",
                                        "pause_task",
                                        "resume_task",
                                        "show_library",
                                        "show_free_space",
                                        "clear_library",
                                        "show_iphone_help",
                                        "open_settings",
                                        "show_help",
                                        "unknown"
                                )
                        ),
                        "confidence", Map.of(
                                "type", "number",
                                "minimum", 0,
                                "maximum", 1
                        ),
                        "arguments", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of(
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
                                ),
                                "properties", Map.of(
                                        "title", nullableString(),
                                        "media_type", nullableEnum("movie", "series"),
                                        "season", Map.of("anyOf", List.of(
                                                Map.of("type", "number"),
                                                Map.of("type", "null")
                                        )),
                                        "episodes", nullableString(),
                                        "quality", nullableEnum("2160p", "1080p", "720p"),
                                        "source", nullableEnum("BDRemux", "WEB-DL", "BDRip"),
                                        "audio", nullableEnum("Дубляж", "Многоголосый", "Original"),
                                        "language", nullableString(),
                                        "destination", nullableEnum("home_pc", "vps", "s3"),
                                        "task_id", nullableString()
                                )
                        ),
                        "reply", nullableString()
                )
        );
    }

    private Map<String, Object> nullableString() {
        return Map.of("anyOf", List.of(
                Map.of("type", "string"),
                Map.of("type", "null")
        ));
    }

    private Map<String, Object> nullableEnum(String... values) {
        return Map.of("anyOf", List.of(
                Map.of("type", "string", "enum", List.of(values)),
                Map.of("type", "null")
        ));
    }
}
