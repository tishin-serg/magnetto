package ru.xataaa.torrentbot.llm;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BotScenarioCatalog {

    public List<String> actions() {
        return List.of(
                "search_media",
                "show_tasks",
                "pause_task",
                "resume_task",
                "show_library",
                "show_free_space",
                "clear_library",
                "show_iphone_help",
                "show_help",
                "open_settings",
                "unknown"
        );
    }

    public String promptText() {
        return """
                Доступные действия бота:
                - search_media: найти фильм или сериал. Только извлеки параметры, не ищи сам.
                - show_tasks: показать задачи.
                - pause_task: пользователь хочет поставить задачу на паузу. Реальное действие требует выбора кнопкой.
                - resume_task: пользователь хочет продолжить задачу. Реальное действие требует выбора кнопкой.
                - show_library: открыть медиатеку.
                - show_free_space: показать свободное место.
                - clear_library: очистить медиатеку, только через подтверждение кнопкой.
                - show_iphone_help: инструкция для iPhone, Infuse или WebDAV.
                - show_help: помощь.
                - open_settings: настройки.
                - unknown: если намерение не относится к возможностям бота или неясно.
                """;
    }
}
