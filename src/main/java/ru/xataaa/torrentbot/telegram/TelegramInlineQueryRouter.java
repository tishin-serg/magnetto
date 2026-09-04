package ru.xataaa.torrentbot.telegram;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.SafeLog;
import ru.xataaa.torrentbot.config.SearchProperties;
import ru.xataaa.torrentbot.movie.MovieMetadata;
import ru.xataaa.torrentbot.movie.MovieMetadataService;

@Slf4j
@Component
public class TelegramInlineQueryRouter {

    private final MovieMetadataService movieMetadataService;
    private final TelegramInlineResultFactory telegramInlineResultFactory;
    private final TelegramMessageService telegramMessageService;
    private final SearchProperties searchProperties;
    private final MeterRegistry meterRegistry;
    private final Executor inlineQueryExecutor;
    private final Map<String, String> latestInlineQueryByUser = new ConcurrentHashMap<>();

    public TelegramInlineQueryRouter(
            MovieMetadataService movieMetadataService,
            TelegramInlineResultFactory telegramInlineResultFactory,
            TelegramMessageService telegramMessageService,
            SearchProperties searchProperties,
            MeterRegistry meterRegistry,
            @Qualifier("inlineQueryExecutor") Executor inlineQueryExecutor
    ) {
        this.movieMetadataService = movieMetadataService;
        this.telegramInlineResultFactory = telegramInlineResultFactory;
        this.telegramMessageService = telegramMessageService;
        this.searchProperties = searchProperties;
        this.meterRegistry = meterRegistry;
        this.inlineQueryExecutor = inlineQueryExecutor;
    }

    public void route(String inlineQueryId, Long userId, String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        String queryHash = SafeLog.sha256Short(normalizedQuery);
        String userKey = userKey(userId, inlineQueryId);
        latestInlineQueryByUser.put(userKey, inlineQueryId);
        log.info("inline_query_received: userId={}, queryHash={}, queryPreview={}",
                userId, queryHash, SafeLog.preview(normalizedQuery, 40));
        inlineQueryExecutor.execute(() -> answerLatestQuery(inlineQueryId, userId, userKey, normalizedQuery, queryHash));
    }

    private void answerLatestQuery(String inlineQueryId, Long userId, String userKey, String normalizedQuery, String queryHash) {
        Timer.Sample sample = Timer.start(meterRegistry);
        List<MovieMetadata> movies = List.of();
        String result = "empty";
        try {
            debounceIfNeeded(normalizedQuery);
            if (isStale(userKey, inlineQueryId)) {
                log.info("inline_query_skipped_stale_before_search: userId={}, queryHash={}", userId, queryHash);
                result = "stale";
                return;
            }
            if (normalizedQuery.length() >= 2) {
                var cachedMovies = movieMetadataService.findCached(normalizedQuery);
                if (cachedMovies.isPresent()) {
                    movies = cachedMovies.get();
                    result = movies.isEmpty() ? "empty" : "success";
                } else {
                    movies = movieMetadataService.search(normalizedQuery);
                    result = movies.isEmpty() ? "empty" : "success";
                }
            }
            if (isStale(userKey, inlineQueryId)) {
                log.info("inline_query_skipped_stale_after_search: userId={}, queryHash={}", userId, queryHash);
                result = "stale";
                return;
            }
            String resultsJson = telegramInlineResultFactory.movieResults(movies);
            telegramMessageService.answerInlineQuery(inlineQueryId, resultsJson, searchProperties.inlineCacheSeconds());
            log.info("inline_answer_sent: userId={}, queryHash={}, resultCount={}", userId, queryHash, movies.size());
        } catch (RuntimeException runtimeException) {
            meterRegistry.counter("search.error", "source", "telegram_inline", "result", "error").increment();
            result = "error";
            log.warn("inline_query_failed: userId={}, queryHash={}, error={}", userId, queryHash, runtimeException.getMessage());
            answerEmptyIfStillLatest(inlineQueryId, userId, userKey, queryHash);
        } finally {
            sample.stop(Timer.builder("telegram.inline.query.duration")
                    .tag("source", "telegram")
                    .tag("result", result)
                    .tag("type", "movie")
                    .register(meterRegistry));
        }
    }

    private void answerEmptyIfStillLatest(String inlineQueryId, Long userId, String userKey, String queryHash) {
        if (isStale(userKey, inlineQueryId)) {
            return;
        }
        try {
            telegramMessageService.answerInlineQuery(inlineQueryId, "[]", 1);
        } catch (RuntimeException runtimeException) {
            meterRegistry.counter("search.error", "source", "telegram_inline_answer", "result", "error").increment();
            log.warn("inline_answer_failed: userId={}, queryHash={}, error={}", userId, queryHash, runtimeException.getMessage());
        }
    }

    private void debounceIfNeeded(String normalizedQuery) {
        if (normalizedQuery.length() < 2) {
            return;
        }
        long debounceMs = Math.max(0L, searchProperties.inlineDebounceMs());
        if (debounceMs == 0L) {
            return;
        }
        try {
            Thread.sleep(debounceMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Inline query debounce interrupted", interruptedException);
        }
    }

    private boolean isStale(String userKey, String inlineQueryId) {
        String latestInlineQueryId = latestInlineQueryByUser.get(userKey);
        return latestInlineQueryId != null && !latestInlineQueryId.equals(inlineQueryId);
    }

    private String userKey(Long userId, String inlineQueryId) {
        if (userId != null) {
            return userId.toString();
        }
        return "anonymous:" + inlineQueryId;
    }
}
