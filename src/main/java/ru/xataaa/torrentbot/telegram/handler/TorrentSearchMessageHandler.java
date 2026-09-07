package ru.xataaa.torrentbot.telegram.handler;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
                    "🔎 Поиск\n\nНапиши название фильма или сериала, например «Матрица 1999».",
                    telegramKeyboardFactory.searchLauncherKeyboard()
            );
            return;
        }
        String queryHash = SafeLog.sha256Short(query);
        telegramMessageService.sendTyping(chatId);
        if (isDirectTorrentSearch(text)) {
            sendDirectTorrentSearch(chatId, query, queryHash);
            return;
        }
        log.info("movie_chat_search_started: chatId={}, queryHash={}, queryPreview={}",
                chatId, queryHash, SafeLog.preview(query, 40));
        Optional<List<MovieMetadata>> cachedMovies = movieMetadataService.findCached(query);
        if (cachedMovies.isPresent() && !cachedMovies.get().isEmpty()) {
            List<MovieMetadata> movies = cachedMovies.get();
            telegramMessageService.sendTextWithInlineKeyboard(
                    chatId,
                    telegramInlineResultFactory.movieCandidatesText(query, movies),
                    telegramInlineResultFactory.movieCandidatesKeyboard(movies)
            );
            log.info("movie_chat_search_cache_hit: chatId={}, queryHash={}, resultCount={}",
                    chatId, queryHash, movies.size());
            return;
        }
        sendDirectTorrentSearch(chatId, query, queryHash);
    }

    private void sendDirectTorrentSearch(Long chatId, String query, String queryHash) {
        sendDirectTorrentSearch(chatId, query, queryHash, CompletableFuture.supplyAsync(() -> torrentSearchService.searchFirstPage(query)));
    }

    private void sendDirectTorrentSearch(Long chatId, String query, String queryHash, CompletableFuture<TorrentSearchService.SearchPage> searchFuture) {
        var progress = telegramMessageService.sendText(chatId, "Ищу раздачи: " + query + "…");
        Long progressId = progress == null ? null : progress.getMessageId();
        TorrentSearchService.SearchPage searchPage;
        try {
            searchPage = searchFuture.get();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            renderSearch(chatId, progressId, "Поиск прерван. Попробуй ещё раз.", telegramKeyboardFactory.searchLauncherKeyboard());
            return;
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause() == null ? executionException : executionException.getCause();
            log.warn("Torrent search failed: chatId={}, queryHash={}, error={}", chatId, queryHash, cause.getMessage());
            renderSearch(chatId, progressId, "Источник раздач не ответил. Попробуй поиск позже.", telegramKeyboardFactory.searchLauncherKeyboard());
            return;
        }
        renderSearch(chatId, progressId, torrentSearchService.formatPageMessage(searchPage)
                        + (searchPage.results().isEmpty() ? "\n\nПопробуй другое название или добавь год выпуска." : ""),
                searchPage.results().isEmpty() ? telegramKeyboardFactory.searchLauncherKeyboard() : torrentSearchService.resultsKeyboard(searchPage));
    }

    private void renderSearch(Long chatId, Long messageId, String text, String keyboard) {
        if (messageId == null) telegramMessageService.sendTextWithInlineKeyboard(chatId, text, keyboard);
        else telegramMessageService.editText(chatId, messageId, text, keyboard);
    }

    private void sendSearchPage(Long chatId, TorrentSearchService.SearchPage searchPage) {
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

    private boolean isDirectTorrentSearch(String text) {
        return text != null && text.trim().startsWith("/search");
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
