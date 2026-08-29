package ru.xataaa.torrentbot.llm;

import java.util.Arrays;

public enum LlmAction {
    SEARCH_MEDIA("search_media"),
    SHOW_TASKS("show_tasks"),
    PAUSE_TASK("pause_task"),
    RESUME_TASK("resume_task"),
    SHOW_LIBRARY("show_library"),
    SHOW_FREE_SPACE("show_free_space"),
    CLEAR_LIBRARY("clear_library"),
    SHOW_IPHONE_HELP("show_iphone_help"),
    SHOW_HELP("show_help"),
    OPEN_SETTINGS("open_settings"),
    UNKNOWN("unknown");

    private final String code;

    LlmAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static LlmAction fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(action -> action.code.equals(code))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
