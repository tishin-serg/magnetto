package ru.xataaa.torrentbot.telegram.handler;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.SafeLog;
import ru.xataaa.torrentbot.config.AppProperties;
import ru.xataaa.torrentbot.config.TelegramProperties;
import ru.xataaa.torrentbot.movie.MovieMetadata;
import ru.xataaa.torrentbot.movie.MovieMetadataService;
import ru.xataaa.torrentbot.telegram.TelegramInlineResultFactory;
import ru.xataaa.torrentbot.telegram.TelegramKeyboardFactory;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchService;

@Slf4j
@Component
@RequiredArgsConstructor
public class TorrentSearchMessageHandler implements TelegramMessageHandler {

    private final TorrentSearchService torrentSearchService;
    private final MovieMetadataService movieMetadataService;
    private final TelegramInlineResultFactory telegramInlineResultFactory;
    private final TelegramKeyboardFactory telegramKeyboardFactory;
    private final TelegramMessageService telegramMessageService;
    private final AppProperties appProperties;
    private final TelegramProperties telegramProperties;

    @Override
    public boolean supports(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmedText = text.trim();
        return trimmedText.startsWith("/search")
                || trimmedText.toLowerCase().startsWith("поиск ")
                || !trimmedText.startsWith("/");
    }

    @Override
    public void handle(Long chatId, String text) {
        if (!appProperties.isChatAllowed(chatId)) {
            telegramMessageService.sendText(chatId, "Доступ запрещён.");
            return;
        }
        String query = normalizeQuery(text);
        if (query.length() < 2) {
            telegramMessageService.sendTextWithInlineKeyboard(
                    chatId,
                    "Нажми \"Найти через TMDb\" и начни вводить название. Карточки появятся над клавиатурой.",
                    telegramKeyboardFactory.searchLauncherKeyboard()
            );
            return;
        }
        String queryHash = SafeLog.sha256Short(query);
        log.info("movie_chat_search_started: chatId={}, queryHash={}, queryPreview={}",
                chatId, queryHash, SafeLog.preview(query, 40));
        try {
            List<MovieMetadata> movies = movieMetadataService.search(query);
            if (!movies.isEmpty()) {
                telegramMessageService.sendTextWithInlineKeyboard(
                        chatId,
                        telegramInlineResultFactory.movieCandidatesText(query, movies),
                        telegramInlineResultFactory.movieCandidatesKeyboard(movies)
                );
                log.info("movie_chat_search_completed: chatId={}, queryHash={}, resultCount={}",
                        chatId, queryHash, movies.size());
                return;
            }
        } catch (RuntimeException runtimeException) {
            log.warn("movie_chat_search_failed: chatId={}, queryHash={}, error={}",
                    chatId, queryHash, runtimeException.getMessage());
        }
        sendDirectTorrentSearch(chatId, query, queryHash);
    }

    private void sendDirectTorrentSearch(Long chatId, String query, String queryHash) {
        TorrentSearchService.SearchPage searchPage;
        try {
            searchPage = torrentSearchService.searchFirstPage(query);
        } catch (RuntimeException runtimeException) {
            log.warn("Torrent search failed: chatId={}, queryHash={}, error={}", chatId, queryHash, runtimeException.getMessage());
            telegramMessageService.sendText(chatId, "Поиск временно недоступен. Внешний источник не ответил, попробуй ещё раз позже.");
            return;
        }
        if (searchPage.results().isEmpty()) {
            telegramMessageService.sendText(chatId, torrentSearchService.formatPageMessage(searchPage));
            return;
        }
        telegramMessageService.sendTextWithInlineKeyboard(
                chatId,
                torrentSearchService.formatPageMessage(searchPage),
                torrentSearchService.resultsKeyboard(searchPage)
        );
    }

    private String normalizeQuery(String text) {
        String trimmedText = text == null ? "" : text.trim();
        if (trimmedText.startsWith("/search")) {
            return stripLeadingBotMention(trimmedText.replaceFirst("^/search(@\\w+)?", "").trim());
        }
        if (trimmedText.toLowerCase().startsWith("поиск ")) {
            return stripLeadingBotMention(trimmedText.substring("поиск ".length()).trim());
        }
        return stripLeadingBotMention(trimmedText);
    }

    private String stripLeadingBotMention(String query) {
        if (query == null || !query.startsWith("@")) {
            return query == null ? "" : query.trim();
        }
        int firstSpaceIndex = query.indexOf(' ');
        if (firstSpaceIndex < 0) {
            return query.trim();
        }
        String mention = query.substring(1, firstSpaceIndex).trim();
        String configuredUsername = telegramProperties.botUsername() == null ? "" : telegramProperties.botUsername().trim();
        if (configuredUsername.equalsIgnoreCase(mention) || mention.toLowerCase().endsWith("_bot")) {
            return query.substring(firstSpaceIndex + 1).trim();
        }
        return query.trim();
    }
}
