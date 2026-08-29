package ru.xataaa.torrentbot.telegram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.movie.MovieMetadata;

@Component
@RequiredArgsConstructor
public class TelegramInlineResultFactory {

    private final ObjectMapper objectMapper;

    public String movieResults(List<MovieMetadata> movies) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (MovieMetadata movie : movies) {
            results.add(movieResult(movie));
        }
        try {
            return objectMapper.writeValueAsString(results);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize inline results", exception);
        }
    }

    public String movieCardText(MovieMetadata movie) {
        StringBuilder text = new StringBuilder();
        text.append("Выбран ")
                .append(movie.isTv() ? "сериал" : "фильм")
                .append(":\n\n")
                .append(titleWithOriginal(movie))
                .append("\n");
        if (movie.rating() != null && movie.rating() > 0) {
            text.append("Рейтинг TMDb: ")
                    .append(String.format(Locale.US, "%.1f", movie.rating()))
                    .append("\n");
        }
        if (movie.overview() != null && !movie.overview().isBlank()) {
            text.append("\n").append(shorten(movie.overview(), 550)).append("\n");
        }
        if (movie.isTv()) {
            text.append("\nДальше выбери сезон и серии, затем озвучку и качество.");
        } else {
            text.append("\nДальше выбери озвучку и качество.");
        }
        text.append("\nПосле этого я покажу подходящие раздачи.");
        return text.toString();
    }

    public String movieCandidatesText(String query, List<MovieMetadata> movies) {
        if (movies.isEmpty()) {
            return "Не нашёл карточки фильма по запросу: " + query
                    + "\n\nПопробуй добавить год или оригинальное название.";
        }
        StringBuilder text = new StringBuilder("Нашёл варианты. Выбери фильм или сериал кнопкой ниже:\n\n");
        int index = 1;
        for (MovieMetadata movie : movies) {
            text.append(index)
                    .append(". ")
                    .append(titleWithOriginal(movie))
                    .append("\n   ")
                    .append(movie.isTv() ? "сериал" : "фильм");
            if (movie.rating() != null && movie.rating() > 0) {
                text.append(" · TMDb ").append(String.format(Locale.US, "%.1f", movie.rating()));
            }
            text.append("\n");
            index++;
            if (index > 7) {
                break;
            }
        }
        return text.toString().trim();
    }

    public String movieCandidatesKeyboard(List<MovieMetadata> movies) {
        StringBuilder keyboard = new StringBuilder("{\"inline_keyboard\":[");
        int index = 1;
        for (MovieMetadata movie : movies) {
            if (index > 1) {
                keyboard.append(",");
            }
            keyboard.append("[{\"text\":\"")
                    .append(escapeJson(candidateButtonText(index, movie)))
                    .append("\",\"callback_data\":\"movie:open:")
                    .append(escapeJson(movie.selectionId()))
                    .append("\"}]");
            index++;
            if (index > 7) {
                break;
            }
        }
        keyboard.append(",[{\"text\":\"Новый поиск\",\"callback_data\":\"menu:search\"}]]}");
        return keyboard.toString();
    }

    private Map<String, Object> movieResult(MovieMetadata movie) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "article");
        result.put("id", movie.selectionId());
        result.put("title", title(movie));
        result.put("description", description(movie));
        if (movie.posterUrl() != null && !movie.posterUrl().isBlank()) {
            result.put("thumbnail_url", movie.posterUrl());
        }
        result.put("input_message_content", Map.of("message_text", movieCardText(movie)));
        result.put("reply_markup", keyboardObject(movie));
        return result;
    }

    private Map<String, Object> keyboardObject(MovieMetadata movie) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        rows.add(List.of(Map.of("text", "Сезон, серии и раздачи", "callback_data", "movie:open:" + movie.selectionId())));
        return Map.of("inline_keyboard", rows);
    }

    private String title(MovieMetadata movie) {
        StringBuilder title = new StringBuilder(movie.title());
        if (movie.year() != null) {
            title.append(" · ").append(movie.year());
        }
        return title.toString();
    }

    private String titleWithOriginal(MovieMetadata movie) {
        StringBuilder title = new StringBuilder(movie.title());
        if (movie.originalTitle() != null && !movie.originalTitle().isBlank()
                && !movie.originalTitle().equalsIgnoreCase(movie.title())) {
            title.append(" / ").append(movie.originalTitle());
        }
        if (movie.year() != null) {
            title.append(" (").append(movie.year()).append(")");
        }
        return title.toString();
    }

    private String candidateButtonText(int index, MovieMetadata movie) {
        StringBuilder text = new StringBuilder();
        text.append(index).append(". ").append(movie.title());
        if (movie.year() != null) {
            text.append(" · ").append(movie.year());
        }
        text.append(movie.isTv() ? " · сериал" : " · фильм");
        return shorten(text.toString(), 58);
    }

    private String description(MovieMetadata movie) {
        StringBuilder description = new StringBuilder();
        description.append(movie.isTv() ? "СЕРИАЛ" : "ФИЛЬМ");
        if (movie.rating() != null && movie.rating() > 0) {
            description.append(" | tmdb:").append(String.format(Locale.US, "%.1f", movie.rating()));
        }
        if (movie.originalTitle() != null && !movie.originalTitle().isBlank()) {
            description.append(" | ").append(movie.originalTitle());
        }
        return shorten(description.toString(), 120);
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
