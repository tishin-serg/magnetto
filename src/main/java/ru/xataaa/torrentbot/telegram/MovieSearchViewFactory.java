package ru.xataaa.torrentbot.telegram;

import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.movie.MovieMetadata;
import ru.xataaa.torrentbot.movie.MovieSearchSession;
import ru.xataaa.torrentbot.movie.TvEpisodeSummary;
import ru.xataaa.torrentbot.movie.TvSeasonDetails;
import ru.xataaa.torrentbot.movie.TvSeasonSummary;
import ru.xataaa.torrentbot.torrentsearch.TorrentAvailabilityCatalog;
import ru.xataaa.torrentbot.torrentsearch.TorrentQuality;
import ru.xataaa.torrentbot.torrentsearch.VoiceFilter;

@Component
@RequiredArgsConstructor
public class MovieSearchViewFactory {

    public String filtersText(MovieSearchSession session) {
        MovieMetadata movie = session.movieMetadata();
        StringBuilder text = new StringBuilder();
        text.append(movie.isTv() ? "Сериал: " : "Фильм: ")
                .append(title(movie))
                .append("\n");
        if (movie.rating() != null && movie.rating() > 0) {
            text.append("Рейтинг TMDb: ")
                    .append(String.format(java.util.Locale.US, "%.1f", movie.rating()))
                    .append("\n");
        }
        text.append("\nДальше выбери сезон и серии, затем озвучку и качество.\n")
                .append("После этого я покажу подходящие раздачи.\n\n")
                .append("Выбрано сейчас:\n")
                .append("Качество: ").append(session.quality().displayName()).append("\n")
                .append("Озвучка: ").append(session.voice().displayName()).append("\n");
        if (movie.isTv()) {
            text.append("Сезон: ").append(session.seasonNumber() == null ? "не выбран" : session.seasonNumber()).append("\n")
                    .append("Серии: ").append(episodesLabel(session.episodeNumbers())).append("\n");
        }
        text.append("\nНажми \"Найти раздачи\", когда выбор готов.");
        return text.toString();
    }

    public String filtersKeyboard(MovieSearchSession session) {
        String sessionId = escapeJson(session.sessionId());
        StringBuilder keyboard = new StringBuilder("{\"inline_keyboard\":[");
        keyboard.append("[{\"text\":\"Найти раздачи\",\"callback_data\":\"movie:releases:")
                .append(sessionId)
                .append("\"}],");
        keyboard.append("[{\"text\":\"Качество: ")
                .append(escapeJson(session.quality().displayName()))
                .append("\",\"callback_data\":\"movie:quality:")
                .append(sessionId)
                .append(":menu\"},");
        keyboard.append("{\"text\":\"Озвучка: ")
                .append(escapeJson(session.voice().displayName()))
                .append("\",\"callback_data\":\"movie:voice:")
                .append(sessionId)
                .append(":menu\"}]");
        if (session.movieMetadata().isTv()) {
            keyboard.append(",[{\"text\":\"Сезон")
                    .append(session.seasonNumber() == null ? "" : ": " + session.seasonNumber())
                    .append("\",\"callback_data\":\"movie:season:")
                    .append(sessionId)
                    .append(":menu\"}]");
            if (session.seasonNumber() != null) {
                keyboard.append(",[{\"text\":\"Серии: ")
                        .append(escapeJson(episodesButtonLabel(session.episodeNumbers())))
                        .append("\",\"callback_data\":\"movie:episode:")
                        .append(sessionId)
                        .append(":menu\"}]");
            }
        }
        keyboard.append(",[{\"text\":\"Новый поиск\",\"callback_data\":\"menu:search\"}]]}");
        return keyboard.toString();
    }

    public String qualityText(MovieSearchSession session) {
        return "Выбери качество для:\n" + title(session.movieMetadata()) + "\n\nСейчас: " + session.quality().displayName();
    }

    public String qualityKeyboard(MovieSearchSession session) {
        StringBuilder keyboard = new StringBuilder("{\"inline_keyboard\":[");
        int index = 0;
        for (TorrentQuality quality : TorrentQuality.values()) {
            if (index > 0) {
                keyboard.append(",");
            }
            keyboard.append("[{\"text\":\"")
                    .append(quality == session.quality() ? "✓ " : "")
                    .append(escapeJson(quality.displayName()))
                    .append("\",\"callback_data\":\"movie:quality:")
                    .append(escapeJson(session.sessionId()))
                    .append(":")
                    .append(quality.code())
                    .append("\"}]");
            index++;
        }
        keyboard.append(",[{\"text\":\"Назад к фильтрам\",\"callback_data\":\"movie:filters:")
                .append(escapeJson(session.sessionId()))
                .append("\"}]]}");
        return keyboard.toString();
    }

    public String voiceText(MovieSearchSession session) {
        return "Выбери озвучку для:\n" + title(session.movieMetadata()) + "\n\nСейчас: " + session.voice().displayName();
    }

    public String voiceKeyboard(MovieSearchSession session) {
        StringBuilder keyboard = new StringBuilder("{\"inline_keyboard\":[");
        int index = 0;
        for (VoiceFilter voiceFilter : VoiceFilter.values()) {
            if (index > 0) {
                keyboard.append(",");
            }
            keyboard.append("[{\"text\":\"")
                    .append(voiceFilter == session.voice() ? "✓ " : "")
                    .append(escapeJson(voiceFilter.displayName()))
                    .append("\",\"callback_data\":\"movie:voice:")
                    .append(escapeJson(session.sessionId()))
                    .append(":")
                    .append(voiceFilter.code())
                    .append("\"}]");
            index++;
        }
        keyboard.append(",[{\"text\":\"Назад к фильтрам\",\"callback_data\":\"movie:filters:")
                .append(escapeJson(session.sessionId()))
                .append("\"}]]}");
        return keyboard.toString();
    }

    public String seasonsText(MovieSearchSession session) {
        return "Выбери сезон для:\n" + title(session.movieMetadata());
    }

    public String seasonsKeyboard(MovieSearchSession session, java.util.List<TvSeasonSummary> seasons) {
        StringBuilder keyboard = new StringBuilder("{\"inline_keyboard\":[");
        int index = 0;
        for (TvSeasonSummary season : seasons) {
            if (index > 0) {
                keyboard.append(",");
            }
            keyboard.append("[{\"text\":\"")
                    .append(session.seasonNumber() != null && session.seasonNumber() == season.seasonNumber() ? "✓ " : "")
                    .append(season.seasonNumber())
                    .append(" сезон");
            if (season.episodeCount() > 0) {
                keyboard.append(" · ").append(season.episodeCount()).append(" серий");
            }
            keyboard.append("\",\"callback_data\":\"movie:season:")
                    .append(escapeJson(session.sessionId()))
                    .append(":")
                    .append(season.seasonNumber())
                    .append("\"}]");
            index++;
        }
        keyboard.append(",[{\"text\":\"Назад к фильтрам\",\"callback_data\":\"movie:filters:")
                .append(escapeJson(session.sessionId()))
                .append("\"}]]}");
        return keyboard.toString();
    }

    public String episodesText(MovieSearchSession session, TvSeasonDetails seasonDetails) {
        return title(session.movieMetadata())
                + "\n"
                + seasonDetails.seasonNumber()
                + " сезон\n\nВыбери одну или несколько серий.";
    }

    public String episodesKeyboard(MovieSearchSession session, TvSeasonDetails seasonDetails) {
        StringBuilder keyboard = new StringBuilder("{\"inline_keyboard\":[");
        keyboard.append("[{\"text\":\"Выбрать все\",\"callback_data\":\"movie:episodes:all:")
                .append(escapeJson(session.sessionId()))
                .append("\"}]");
        int column = 0;
        for (TvEpisodeSummary episode : seasonDetails.episodes()) {
            if (column == 0) {
                keyboard.append(",[");
            } else {
                keyboard.append(",");
            }
            keyboard.append("{\"text\":\"")
                    .append(session.episodeNumbers().contains(episode.episodeNumber()) ? "✓ " : "")
                    .append(episode.episodeNumber())
                    .append("\",\"callback_data\":\"movie:episode:")
                    .append(escapeJson(session.sessionId()))
                    .append(":")
                    .append(episode.episodeNumber())
                    .append("\"}");
            column++;
            if (column == 5) {
                keyboard.append("]");
                column = 0;
            }
        }
        if (column != 0) {
            keyboard.append("]");
        }
        keyboard.append(",[{\"text\":\"Продолжить\",\"callback_data\":\"movie:filters:")
                .append(escapeJson(session.sessionId()))
                .append("\"}]");
        keyboard.append(",[{\"text\":\"Назад к фильтрам\",\"callback_data\":\"movie:filters:")
                .append(escapeJson(session.sessionId()))
                .append("\"}]]}");
        return keyboard.toString();
    }

    public String noResultsKeyboard(MovieSearchSession session) {
        return "{\"inline_keyboard\":["
                + "[{\"text\":\"Сбросить качество\",\"callback_data\":\"movie:quality:" + escapeJson(session.sessionId()) + ":any\"}],"
                + "[{\"text\":\"Сбросить озвучку\",\"callback_data\":\"movie:voice:" + escapeJson(session.sessionId()) + ":any\"}],"
                + "[{\"text\":\"Назад к фильтрам\",\"callback_data\":\"movie:filters:" + escapeJson(session.sessionId()) + "\"}],"
                + "[{\"text\":\"Новый поиск\",\"callback_data\":\"menu:search\"}]]}";
    }

    public String noAvailabilityText(MovieSearchSession session) {
        return "Не нашёл доступные раздачи во внешнем поиске для:\n"
                + title(session.movieMetadata())
                + "\n\nМожно попробовать новый поиск или обычный ручной поиск по названию.";
    }

    public String noAvailabilityKeyboard() {
        return "{\"inline_keyboard\":[[{\"text\":\"Новый поиск\",\"callback_data\":\"menu:search\"}]]}";
    }

    public String availableSeasonsText(MovieSearchSession session, TorrentAvailabilityCatalog catalog) {
        StringBuilder text = new StringBuilder(title(session.movieMetadata()))
                .append("\nДоступные сезоны\n\n");
        for (TorrentAvailabilityCatalog.SeasonOption season : catalog.seasonOptions()) {
            text.append("Сезон ")
                    .append(season.seasonNumber())
                    .append(" · раздач: ")
                    .append(season.resultCount())
                    .append(" · сидов до ")
                    .append(season.maxSeeders())
                    .append("\n");
        }
        if (catalog.multiSeasonPackCount() > 0) {
            text.append("\nТакже есть паки нескольких сезонов: ")
                    .append(catalog.multiSeasonPackCount());
        }
        return text.toString().trim();
    }

    public String availableSeasonsKeyboard(MovieSearchSession session, TorrentAvailabilityCatalog catalog) {
        StringBuilder keyboard = new StringBuilder("{\"inline_keyboard\":[");
        int index = 0;
        for (TorrentAvailabilityCatalog.SeasonOption season : catalog.seasonOptions()) {
            if (index > 0) {
                keyboard.append(",");
            }
            keyboard.append("[{\"text\":\"")
                    .append(session.seasonNumber() != null && session.seasonNumber() == season.seasonNumber() ? "✓ " : "")
                    .append("Сезон ")
                    .append(season.seasonNumber())
                    .append(" · сидов до ")
                    .append(season.maxSeeders())
                    .append("\",\"callback_data\":\"movie:season:")
                    .append(escapeJson(session.sessionId()))
                    .append(":")
                    .append(season.seasonNumber())
                    .append("\"}]");
            index++;
        }
        keyboard.append(",[{\"text\":\"Новый поиск\",\"callback_data\":\"menu:search\"}]]}");
        return keyboard.toString();
    }

    public String availableScopesText(MovieSearchSession session, TorrentAvailabilityCatalog catalog) {
        StringBuilder text = new StringBuilder(title(session.movieMetadata()))
                .append("\nСезон ")
                .append(session.seasonNumber())
                .append("\n\nЧто скачать?\n\n");
        for (TorrentAvailabilityCatalog.ScopeOption scope : catalog.scopeOptions(session.seasonNumber())) {
            text.append(scope.label())
                    .append(" · раздач: ")
                    .append(scope.resultCount())
                    .append(" · сидов до ")
                    .append(scope.maxSeeders())
                    .append("\n");
        }
        return text.toString().trim();
    }

    public String availableScopesKeyboard(MovieSearchSession session, TorrentAvailabilityCatalog catalog) {
        StringBuilder keyboard = new StringBuilder("{\"inline_keyboard\":[");
        int index = 0;
        for (TorrentAvailabilityCatalog.ScopeOption scope : catalog.scopeOptions(session.seasonNumber())) {
            if (index > 0) {
                keyboard.append(",");
            }
            keyboard.append("[{\"text\":\"")
                    .append(scope.code().equals(session.availabilityScope()) ? "✓ " : "")
                    .append(escapeJson(scope.label()))
                    .append(" · сидов до ")
                    .append(scope.maxSeeders())
                    .append("\",\"callback_data\":\"movie:availableScope:")
                    .append(escapeJson(session.sessionId()))
                    .append(":")
                    .append(index)
                    .append("\"}]");
            index++;
        }
        keyboard.append(",[{\"text\":\"Назад к сезонам\",\"callback_data\":\"movie:season:")
                .append(escapeJson(session.sessionId()))
                .append(":menu\"}],");
        keyboard.append("[{\"text\":\"Новый поиск\",\"callback_data\":\"menu:search\"}]]}");
        return keyboard.toString();
    }

    public String availableVoicesText(MovieSearchSession session, TorrentAvailabilityCatalog catalog) {
        StringBuilder text = new StringBuilder(availabilityScopeLabel(session))
                .append("\nВыбери озвучку\n\n")
                .append("Любая озвучка означает, что я не ограничиваю выдачу по студии.\n")
                .append("Если студию не удалось распознать, я покажу это отдельно.\n\n");
        for (TorrentAvailabilityCatalog.VoiceOption voice : catalog.voiceOptions(session.seasonNumber(), session.availabilityScope())) {
            text.append(voiceLabel(voice.voice()))
                    .append(" · раздач: ")
                    .append(voice.resultCount())
                    .append(" · сидов до ")
                    .append(voice.maxSeeders())
                    .append("\n");
        }
        return text.toString().trim();
    }

    public String availableVoicesKeyboard(MovieSearchSession session, TorrentAvailabilityCatalog catalog) {
        StringBuilder keyboard = new StringBuilder("{\"inline_keyboard\":[");
        int index = 0;
        for (TorrentAvailabilityCatalog.VoiceOption voice : catalog.voiceOptions(session.seasonNumber(), session.availabilityScope())) {
            if (index > 0) {
                keyboard.append(",");
            }
            keyboard.append("[{\"text\":\"")
                    .append(voice.voice().equalsIgnoreCase(session.availabilityVoice() == null ? "" : session.availabilityVoice()) ? "✓ " : "")
                    .append(escapeJson(shorten(voiceButtonLabel(voice.voice()), 34)))
                    .append(" · ")
                    .append(voice.resultCount())
                    .append(" · сидов ")
                    .append(voice.maxSeeders())
                    .append("\",\"callback_data\":\"movie:availableVoice:")
                    .append(escapeJson(session.sessionId()))
                    .append(":")
                    .append(index)
                    .append("\"}]");
            index++;
        }
        appendAvailabilityBackRows(keyboard, session);
        return keyboard.toString();
    }

    public String availableQualitiesText(MovieSearchSession session, TorrentAvailabilityCatalog catalog) {
        StringBuilder text = new StringBuilder(availabilityScopeLabel(session))
                .append(" · ")
                .append(session.availabilityVoice() == null ? "Озвучка не выбрана" : voiceLabel(session.availabilityVoice()))
                .append("\nВыбери качество\n\n")
                .append("Можно выбрать понятную группу или открыть технический вариант ниже.\n\n");
        for (TorrentAvailabilityCatalog.QualityOption quality : catalog.qualityOptions(session.seasonNumber(), session.availabilityScope(), session.availabilityVoice())) {
            text.append(qualityLabel(quality.quality()))
                    .append(" · раздач: ")
                    .append(quality.resultCount())
                    .append(" · сидов до ")
                    .append(quality.maxSeeders())
                    .append("\n");
        }
        return text.toString().trim();
    }

    public String availableQualitiesKeyboard(MovieSearchSession session, TorrentAvailabilityCatalog catalog) {
        StringBuilder keyboard = new StringBuilder("{\"inline_keyboard\":[");
        int index = 0;
        for (TorrentAvailabilityCatalog.QualityOption quality : catalog.qualityOptions(session.seasonNumber(), session.availabilityScope(), session.availabilityVoice())) {
            if (index > 0) {
                keyboard.append(",");
            }
            keyboard.append("[{\"text\":\"")
                    .append(quality.quality().equalsIgnoreCase(session.availabilityQuality() == null ? "" : session.availabilityQuality()) ? "✓ " : "")
                    .append(escapeJson(qualityButtonLabel(quality.quality())))
                    .append(" · сидов ")
                    .append(quality.maxSeeders())
                    .append("\",\"callback_data\":\"movie:availableQuality:")
                    .append(escapeJson(session.sessionId()))
                    .append(":")
                    .append(index)
                    .append("\"}]");
            index++;
        }
        appendAvailabilityBackRows(keyboard, session);
        return keyboard.toString();
    }

    public String availabilitySummaryText(MovieSearchSession session, TorrentAvailabilityCatalog catalog) {
        int count = catalog.filtered(session.seasonNumber(), session.availabilityScope(), session.availabilityVoice(), session.availabilityQuality()).size();
        return availabilityScopeLabel(session)
                + "\n\nОзвучка: " + voiceLabel(session.availabilityVoice())
                + "\nКачество: " + qualityLabel(session.availabilityQuality())
                + "\nПодходящих раздач: " + count;
    }

    public String availabilitySummaryKeyboard(MovieSearchSession session) {
        String sessionId = escapeJson(session.sessionId());
        StringBuilder keyboard = new StringBuilder("{\"inline_keyboard\":[");
        keyboard.append("[{\"text\":\"Показать раздачи\",\"callback_data\":\"movie:releases:")
                .append(sessionId)
                .append("\"}],");
        keyboard.append("[{\"text\":\"Озвучка: ")
                .append(escapeJson(shorten(voiceButtonLabel(session.availabilityVoice()), 28)))
                .append("\",\"callback_data\":\"movie:availableVoice:")
                .append(sessionId)
                .append(":menu\"}],");
        keyboard.append("[{\"text\":\"Качество: ")
                .append(escapeJson(qualityButtonLabel(session.availabilityQuality())))
                .append("\",\"callback_data\":\"movie:availableQuality:")
                .append(sessionId)
                .append(":menu\"}]");
        if (session.movieMetadata().isTv()) {
            keyboard.append(",[{\"text\":\"Что скачать\",\"callback_data\":\"movie:availableScope:")
                    .append(sessionId)
                    .append(":menu\"}],");
            keyboard.append("[{\"text\":\"Сезон ")
                    .append(session.seasonNumber())
                    .append("\",\"callback_data\":\"movie:season:")
                    .append(sessionId)
                    .append(":menu\"}]");
        }
        keyboard.append(",[{\"text\":\"Новый поиск\",\"callback_data\":\"menu:search\"}]]}");
        return keyboard.toString();
    }

    public String availabilityQueryLabel(MovieSearchSession session) {
        StringBuilder label = new StringBuilder(title(session.movieMetadata()));
        String scope = availabilityScopeShortLabel(session);
        if (!scope.isBlank()) {
            label.append(", ").append(scope);
        }
        if (session.availabilityVoice() != null) {
            label.append(", ").append(session.availabilityVoice());
        }
        if (session.availabilityQuality() != null) {
            label.append(", ").append(session.availabilityQuality());
        }
        return label.toString();
    }

    private String title(MovieMetadata movie) {
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

    private String episodesLabel(Set<Integer> episodeNumbers) {
        if (episodeNumbers == null || episodeNumbers.isEmpty()) {
            return "не выбраны";
        }
        return new TreeSet<>(episodeNumbers).toString();
    }

    private String episodesButtonLabel(Set<Integer> episodeNumbers) {
        if (episodeNumbers == null || episodeNumbers.isEmpty()) {
            return "выбрать";
        }
        return new TreeSet<>(episodeNumbers).size() + " выбрано";
    }

    private String availabilityScopeLabel(MovieSearchSession session) {
        StringBuilder label = new StringBuilder(title(session.movieMetadata()));
        String scope = availabilityScopeShortLabel(session);
        if (!scope.isBlank()) {
            label.append("\n").append(scope);
        }
        return label.toString();
    }

    private String availabilityScopeShortLabel(MovieSearchSession session) {
        if (!session.movieMetadata().isTv() || session.seasonNumber() == null) {
            return "";
        }
        if ("season-pack".equals(session.availabilityScope())) {
            return "Сезон " + session.seasonNumber() + " целиком";
        }
        if ("episodes".equals(session.availabilityScope())) {
            return session.episodeNumbers() == null || session.episodeNumbers().isEmpty()
                    ? "Отдельные серии сезона " + session.seasonNumber()
                    : "Сезон " + session.seasonNumber() + ", серии " + new TreeSet<>(session.episodeNumbers());
        }
        if ("multi-season".equals(session.availabilityScope())) {
            return "Паки сезонов";
        }
        return "Сезон " + session.seasonNumber();
    }

    private String voiceLabel(String voice) {
        if (voice == null || voice.isBlank()) {
            return "не выбрана";
        }
        if (TorrentAvailabilityCatalog.ANY_VOICE.equalsIgnoreCase(voice)) {
            return "Любая озвучка";
        }
        if (ru.xataaa.torrentbot.torrentsearch.TorrentAvailabilityItem.UNKNOWN_VOICE.equalsIgnoreCase(voice)) {
            return "Не удалось распознать";
        }
        return voice;
    }

    private String voiceButtonLabel(String voice) {
        return voiceLabel(voice);
    }

    private String qualityLabel(String quality) {
        if (quality == null || quality.isBlank()) {
            return "не выбрано";
        }
        if (TorrentAvailabilityCatalog.ANY_QUALITY.equalsIgnoreCase(quality)) {
            return "Любое качество";
        }
        if (TorrentAvailabilityCatalog.QUALITY_OPTIMAL.equalsIgnoreCase(quality)) {
            return "Оптимально - хороший баланс";
        }
        if (TorrentAvailabilityCatalog.QUALITY_SMALL.equalsIgnoreCase(quality)) {
            return "Меньше размер";
        }
        if (TorrentAvailabilityCatalog.QUALITY_MAX.equalsIgnoreCase(quality)) {
            return "Максимальное качество";
        }
        return quality;
    }

    private String qualityButtonLabel(String quality) {
        if (TorrentAvailabilityCatalog.QUALITY_OPTIMAL.equalsIgnoreCase(quality)) {
            return "Оптимально";
        }
        return qualityLabel(quality);
    }

    private void appendAvailabilityBackRows(StringBuilder keyboard, MovieSearchSession session) {
        if (session.movieMetadata().isTv()) {
            keyboard.append(",[{\"text\":\"Назад\",\"callback_data\":\"movie:availableScope:")
                    .append(escapeJson(session.sessionId()))
                    .append(":menu\"}]");
        }
        keyboard.append(",[{\"text\":\"Новый поиск\",\"callback_data\":\"menu:search\"}]]}");
    }

    private String shorten(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
