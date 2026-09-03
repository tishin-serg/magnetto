package ru.xataaa.torrentbot.telegram;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.SafeLog;
import ru.xataaa.torrentbot.movie.MovieMetadata;
import ru.xataaa.torrentbot.movie.MovieMetadataService;
import ru.xataaa.torrentbot.movie.MovieSearchSession;
import ru.xataaa.torrentbot.movie.MovieSearchSessionService;
import ru.xataaa.torrentbot.movie.TvEpisodeSummary;
import ru.xataaa.torrentbot.movie.TvSeasonDetails;
import ru.xataaa.torrentbot.torrentsearch.TorrentAvailabilityCatalog;
import ru.xataaa.torrentbot.torrentsearch.TorrentAvailabilityItem;
import ru.xataaa.torrentbot.torrentsearch.TorrentAvailabilityService;
import ru.xataaa.torrentbot.torrentsearch.TorrentQuality;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchResult;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchService;
import ru.xataaa.torrentbot.torrentsearch.VoiceFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovieSelectionCallbackHandler implements TelegramCallbackHandler {

    private static final String OPEN_PREFIX = "movie:open:";
    private static final String LEGACY_SEARCH_PREFIX = "movie:search:";
    private static final String RELEASES_PREFIX = "movie:releases:";
    private static final String QUALITY_PREFIX = "movie:quality:";
    private static final String VOICE_PREFIX = "movie:voice:";
    private static final String AVAILABLE_SCOPE_PREFIX = "movie:availableScope:";
    private static final String AVAILABLE_VOICE_PREFIX = "movie:availableVoice:";
    private static final String AVAILABLE_QUALITY_PREFIX = "movie:availableQuality:";
    private static final String SEASON_PREFIX = "movie:season:";
    private static final String EPISODE_PREFIX = "movie:episode:";
    private static final String ALL_EPISODES_PREFIX = "movie:episodes:all:";
    private static final String FILTERS_PREFIX = "movie:filters:";

    private final MovieMetadataService movieMetadataService;
    private final MovieSearchSessionService movieSearchSessionService;
    private final MovieSearchViewFactory movieSearchViewFactory;
    private final TorrentSearchService torrentSearchService;
    private final TorrentAvailabilityService torrentAvailabilityService;
    private final TelegramMessageService telegramMessageService;
    private final MeterRegistry meterRegistry;

    @Override
    public boolean supports(String data) {
        return data != null && (data.startsWith(OPEN_PREFIX)
                || data.startsWith(LEGACY_SEARCH_PREFIX)
                || data.startsWith(RELEASES_PREFIX)
                || data.startsWith(QUALITY_PREFIX)
                || data.startsWith(VOICE_PREFIX)
                || data.startsWith(AVAILABLE_SCOPE_PREFIX)
                || data.startsWith(AVAILABLE_VOICE_PREFIX)
                || data.startsWith(AVAILABLE_QUALITY_PREFIX)
                || data.startsWith(SEASON_PREFIX)
                || data.startsWith(EPISODE_PREFIX)
                || data.startsWith(ALL_EPISODES_PREFIX)
                || data.startsWith(FILTERS_PREFIX));
    }

    @Override
    public void handle(String callbackQueryId, Long chatId, Long messageId, String data) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            if (data.startsWith(OPEN_PREFIX)) {
                handleOpen(callbackQueryId, chatId, messageId, data.substring(OPEN_PREFIX.length()));
                return;
            }
            if (data.startsWith(LEGACY_SEARCH_PREFIX)) {
                handleOpen(callbackQueryId, chatId, messageId, data.substring(LEGACY_SEARCH_PREFIX.length()));
                return;
            }
            if (data.startsWith(RELEASES_PREFIX)) {
                handleReleases(callbackQueryId, chatId, messageId, data.substring(RELEASES_PREFIX.length()));
                return;
            }
            if (data.startsWith(QUALITY_PREFIX)) {
                handleQuality(callbackQueryId, chatId, messageId, data.substring(QUALITY_PREFIX.length()));
                return;
            }
            if (data.startsWith(VOICE_PREFIX)) {
                handleVoice(callbackQueryId, chatId, messageId, data.substring(VOICE_PREFIX.length()));
                return;
            }
            if (data.startsWith(AVAILABLE_SCOPE_PREFIX)) {
                handleAvailableScope(callbackQueryId, chatId, messageId, data.substring(AVAILABLE_SCOPE_PREFIX.length()));
                return;
            }
            if (data.startsWith(AVAILABLE_VOICE_PREFIX)) {
                handleAvailableVoice(callbackQueryId, chatId, messageId, data.substring(AVAILABLE_VOICE_PREFIX.length()));
                return;
            }
            if (data.startsWith(AVAILABLE_QUALITY_PREFIX)) {
                handleAvailableQuality(callbackQueryId, chatId, messageId, data.substring(AVAILABLE_QUALITY_PREFIX.length()));
                return;
            }
            if (data.startsWith(SEASON_PREFIX)) {
                handleSeason(callbackQueryId, chatId, messageId, data.substring(SEASON_PREFIX.length()));
                return;
            }
            if (data.startsWith(EPISODE_PREFIX)) {
                handleEpisode(callbackQueryId, chatId, messageId, data.substring(EPISODE_PREFIX.length()));
                return;
            }
            if (data.startsWith(ALL_EPISODES_PREFIX)) {
                handleAllEpisodes(callbackQueryId, chatId, messageId, data.substring(ALL_EPISODES_PREFIX.length()));
                return;
            }
            if (data.startsWith(FILTERS_PREFIX)) {
                handleFilters(callbackQueryId, chatId, messageId, data.substring(FILTERS_PREFIX.length()));
            }
        } finally {
            sample.stop(Timer.builder("telegram.callback.duration")
                    .tag("callback", "movie")
                    .register(meterRegistry));
        }
    }

    @Override
    public void handleInline(String callbackQueryId, String inlineMessageId, Long userId, String data) {
        handle(callbackQueryId, userId, null, data);
    }

    private void handleOpen(String callbackQueryId, Long chatId, Long messageId, String selectionId) {
        MovieMetadata movieMetadata = movieMetadataService.findBySelectionId(selectionId).orElse(null);
        if (movieMetadata == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Карточка устарела");
            if (chatId != null) {
                telegramMessageService.sendText(chatId, "Карточка фильма устарела. Повтори поиск ещё раз.");
            }
            return;
        }
        MovieSearchSession session = movieSearchSessionService.create(movieMetadata);
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Ищу сезоны и раздачи");
        telegramMessageService.sendTyping(chatId);
        render(chatId, messageId, "Ищу сезоны и доступные раздачи...\nЭто может занять до 10 секунд.", null);
        log.info("movie_filter_opened: chatId={}, searchSessionId={}, tmdbId={}, type={}, title={}, year={}",
                chatId,
                session.sessionId(),
                movieMetadata.tmdbId(),
                movieMetadata.mediaType(),
                SafeLog.preview(movieMetadata.title(), 60),
                movieMetadata.year());
        renderFilters(chatId, messageId, session);
    }

    private void handleReleases(String callbackQueryId, Long chatId, Long messageId, String sessionId) {
        MovieSearchSession session = movieSearchSessionService.find(sessionId).orElse(null);
        if (session == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Фильтры устарели");
            if (chatId != null) {
                telegramMessageService.sendText(chatId, "Фильтры поиска устарели. Открой карточку фильма ещё раз.");
            }
            return;
        }
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Ищу раздачи");
        telegramMessageService.sendTyping(chatId);
        render(chatId, messageId, "Ищу подходящие раздачи во внешнем поиске...\nЕсли источник отвечает медленно, это может занять несколько секунд.", null);
        log.info("movie_releases_search_started: chatId={}, searchSessionId={}, tmdbId={}, mediaType={}, quality={}, voice={}, season={}, episodes={}",
                chatId,
                session.sessionId(),
                session.movieMetadata().tmdbId(),
                session.movieMetadata().mediaType(),
                session.quality().code(),
                session.voice().code(),
                session.seasonNumber(),
                session.episodeNumbers());
        try {
            TorrentSearchService.SearchPage searchPage = searchAvailabilityPage(session);
            meterRegistry.counter("torrent.results.rendered", "source", "jacred", "result",
                    searchPage.results().isEmpty() ? "empty" : "success").increment();
            if (chatId == null) {
                return;
            }
            String text = torrentSearchService.formatPageMessage(searchPage);
            String keyboard = searchPage.results().isEmpty()
                    ? movieSearchViewFactory.noResultsKeyboard(session)
                    : torrentSearchService.resultsKeyboard(searchPage);
            render(chatId, messageId, text, keyboard);
        } catch (RuntimeException runtimeException) {
            meterRegistry.counter("search.error", "source", "jacred", "result", "error").increment();
            log.warn("movie_torrent_search_failed: chatId={}, searchSessionId={}, tmdbId={}, error={}",
                    chatId, session.sessionId(), session.movieMetadata().tmdbId(), runtimeException.getMessage());
            if (chatId != null) {
                telegramMessageService.sendText(chatId, "Поиск раздач временно недоступен. Внешний источник не ответил, попробуй ещё раз позже или измени фильтры.");
            }
        }
    }

    private void handleQuality(String callbackQueryId, Long chatId, Long messageId, String payload) {
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Не понял качество");
            return;
        }
        MovieSearchSession session = movieSearchSessionService.find(parts[0]).orElse(null);
        if (session == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Фильтры устарели");
            return;
        }
        if ("menu".equals(parts[1])) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Выбери качество");
            renderQualityCatalog(chatId, messageId, session);
            return;
        }
        MovieSearchSession updatedSession = movieSearchSessionService.updateQuality(parts[0], TorrentQuality.fromCode(parts[1]))
                .orElse(session);
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Качество: " + updatedSession.quality().displayName());
        renderFilters(chatId, messageId, updatedSession);
    }

    private void handleVoice(String callbackQueryId, Long chatId, Long messageId, String payload) {
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Не понял озвучку");
            return;
        }
        MovieSearchSession session = movieSearchSessionService.find(parts[0]).orElse(null);
        if (session == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Фильтры устарели");
            return;
        }
        if ("menu".equals(parts[1])) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Выбери озвучку");
            renderVoiceCatalog(chatId, messageId, session);
            return;
        }
        MovieSearchSession updatedSession = movieSearchSessionService.updateVoice(parts[0], VoiceFilter.fromCode(parts[1]))
                .orElse(session);
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Озвучка: " + updatedSession.voice().displayName());
        renderFilters(chatId, messageId, updatedSession);
    }

    private void handleAvailableVoice(String callbackQueryId, Long chatId, Long messageId, String payload) {
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Не понял озвучку");
            return;
        }
        MovieSearchSession session = movieSearchSessionService.find(parts[0]).orElse(null);
        if (session == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Фильтры устарели");
            return;
        }
        if ("menu".equals(parts[1])) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Выбери озвучку");
            renderVoiceCatalog(chatId, messageId, session);
            return;
        }
        TorrentAvailabilityCatalog catalog = torrentAvailabilityService.catalog(session.movieMetadata());
        int index = Integer.parseInt(parts[1]);
        List<TorrentAvailabilityCatalog.VoiceOption> voices = catalog.voiceOptions(session.seasonNumber(), session.availabilityScope());
        if (index < 0 || index >= voices.size()) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Озвучка устарела");
            renderVoiceCatalog(chatId, messageId, session);
            return;
        }
        MovieSearchSession updatedSession = movieSearchSessionService.updateAvailabilityVoice(parts[0], voices.get(index).voice()).orElse(session);
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Озвучка: " + updatedSession.availabilityVoice());
        renderQualityCatalog(chatId, messageId, updatedSession);
    }

    private void handleAvailableScope(String callbackQueryId, Long chatId, Long messageId, String payload) {
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Не понял тип раздачи");
            return;
        }
        MovieSearchSession session = movieSearchSessionService.find(parts[0]).orElse(null);
        if (session == null || !session.movieMetadata().isTv() || session.seasonNumber() == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Фильтры устарели");
            return;
        }
        if ("menu".equals(parts[1])) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Выбери, что скачать");
            renderScopeCatalog(chatId, messageId, session);
            return;
        }
        TorrentAvailabilityCatalog catalog = torrentAvailabilityService.catalog(session.movieMetadata());
        int index = Integer.parseInt(parts[1]);
        List<TorrentAvailabilityCatalog.ScopeOption> scopes = catalog.scopeOptions(session.seasonNumber());
        if (index < 0 || index >= scopes.size()) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Вариант устарел");
            renderScopeCatalog(chatId, messageId, session);
            return;
        }
        TorrentAvailabilityCatalog.ScopeOption scope = scopes.get(index);
        MovieSearchSession updatedSession = movieSearchSessionService.updateAvailabilityScope(parts[0], scope.code()).orElse(session);
        telegramMessageService.answerCallbackQuery(callbackQueryId, scope.label());
        if ("episodes".equals(scope.code())) {
            TvSeasonDetails seasonDetails = availableEpisodeDetails(updatedSession, catalog);
            render(chatId, messageId, movieSearchViewFactory.episodesText(updatedSession, seasonDetails), movieSearchViewFactory.episodesKeyboard(updatedSession, seasonDetails));
            return;
        }
        renderVoiceCatalog(chatId, messageId, updatedSession);
    }

    private void handleAvailableQuality(String callbackQueryId, Long chatId, Long messageId, String payload) {
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Не понял качество");
            return;
        }
        MovieSearchSession session = movieSearchSessionService.find(parts[0]).orElse(null);
        if (session == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Фильтры устарели");
            return;
        }
        if ("menu".equals(parts[1])) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Выбери качество");
            renderQualityCatalog(chatId, messageId, session);
            return;
        }
        TorrentAvailabilityCatalog catalog = torrentAvailabilityService.catalog(session.movieMetadata());
        int index = Integer.parseInt(parts[1]);
        List<TorrentAvailabilityCatalog.QualityOption> qualities = catalog.qualityOptions(session.seasonNumber(), session.availabilityScope(), session.availabilityVoice());
        if (index < 0 || index >= qualities.size()) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Качество устарело");
            renderQualityCatalog(chatId, messageId, session);
            return;
        }
        MovieSearchSession updatedSession = movieSearchSessionService.updateAvailabilityQuality(parts[0], qualities.get(index).quality()).orElse(session);
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Качество: " + updatedSession.availabilityQuality());
        renderFilters(chatId, messageId, updatedSession);
    }

    private void handleSeason(String callbackQueryId, Long chatId, Long messageId, String payload) {
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Не понял сезон");
            return;
        }
        MovieSearchSession session = movieSearchSessionService.find(parts[0]).orElse(null);
        if (session == null || !session.movieMetadata().isTv()) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Фильтры устарели");
            return;
        }
        if ("menu".equals(parts[1])) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Выбери сезон");
            TorrentAvailabilityCatalog catalog = torrentAvailabilityService.catalog(session.movieMetadata());
            render(chatId, messageId, movieSearchViewFactory.availableSeasonsText(session, catalog), movieSearchViewFactory.availableSeasonsKeyboard(session, catalog));
            return;
        }
        int seasonNumber = Integer.parseInt(parts[1]);
        MovieSearchSession updatedSession = movieSearchSessionService.selectSeason(parts[0], seasonNumber).orElse(session);
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Сезон " + seasonNumber);
        renderScopeCatalog(chatId, messageId, updatedSession);
    }

    private void handleEpisode(String callbackQueryId, Long chatId, Long messageId, String payload) {
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Не понял серию");
            return;
        }
        MovieSearchSession session = movieSearchSessionService.find(parts[0]).orElse(null);
        if (session == null || session.seasonNumber() == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Сначала выбери сезон");
            return;
        }
        if ("menu".equals(parts[1])) {
            TvSeasonDetails seasonDetails = availableEpisodeDetails(session);
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Выбери серии");
            render(chatId, messageId, movieSearchViewFactory.episodesText(session, seasonDetails), movieSearchViewFactory.episodesKeyboard(session, seasonDetails));
            return;
        }
        int episodeNumber = Integer.parseInt(parts[1]);
        MovieSearchSession updatedSession = movieSearchSessionService.toggleEpisode(parts[0], episodeNumber).orElse(session);
        TvSeasonDetails seasonDetails = availableEpisodeDetails(updatedSession);
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Серия " + episodeNumber);
        render(chatId, messageId, movieSearchViewFactory.episodesText(updatedSession, seasonDetails), movieSearchViewFactory.episodesKeyboard(updatedSession, seasonDetails));
    }

    private void handleAllEpisodes(String callbackQueryId, Long chatId, Long messageId, String sessionId) {
        MovieSearchSession session = movieSearchSessionService.selectAllEpisodes(sessionId).orElse(null);
        if (session == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Фильтры устарели");
            return;
        }
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Выбраны все серии");
        TvSeasonDetails seasonDetails = availableEpisodeDetails(session);
        render(chatId, messageId, movieSearchViewFactory.episodesText(session, seasonDetails), movieSearchViewFactory.episodesKeyboard(session, seasonDetails));
    }

    private void handleFilters(String callbackQueryId, Long chatId, Long messageId, String sessionId) {
        MovieSearchSession session = movieSearchSessionService.find(sessionId).orElse(null);
        if (session == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Фильтры устарели");
            return;
        }
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Фильтры");
        renderFilters(chatId, messageId, session);
    }

    private void renderFilters(Long chatId, Long messageId, MovieSearchSession session) {
        TorrentAvailabilityCatalog catalog = torrentAvailabilityService.catalog(session.movieMetadata());
        if (catalog.empty()) {
            render(chatId, messageId, movieSearchViewFactory.noAvailabilityText(session), movieSearchViewFactory.noAvailabilityKeyboard());
            return;
        }
        if (session.movieMetadata().isTv() && session.seasonNumber() == null) {
            render(chatId, messageId, movieSearchViewFactory.availableSeasonsText(session, catalog), movieSearchViewFactory.availableSeasonsKeyboard(session, catalog));
            return;
        }
        if (session.movieMetadata().isTv()
                && (session.availabilityScope() == null
                || catalog.scopeOptions(session.seasonNumber()).stream().noneMatch(option -> option.code().equals(session.availabilityScope())))) {
            renderScopeCatalog(chatId, messageId, session);
            return;
        }
        if (session.movieMetadata().isTv()
                && "episodes".equals(session.availabilityScope())
                && (session.episodeNumbers() == null || session.episodeNumbers().isEmpty())) {
            TvSeasonDetails seasonDetails = availableEpisodeDetails(session, catalog);
            render(chatId, messageId, movieSearchViewFactory.episodesText(session, seasonDetails), movieSearchViewFactory.episodesKeyboard(session, seasonDetails));
            return;
        }
        if (session.availabilityVoice() == null || !catalog.hasVoice(session.availabilityVoice(), session.seasonNumber(), session.availabilityScope())) {
            renderVoiceCatalog(chatId, messageId, session);
            return;
        }
        if (session.availabilityQuality() == null || !catalog.hasQuality(session.availabilityQuality(), session.seasonNumber(), session.availabilityScope(), session.availabilityVoice())) {
            renderQualityCatalog(chatId, messageId, session);
            return;
        }
        render(chatId, messageId, movieSearchViewFactory.availabilitySummaryText(session, catalog), movieSearchViewFactory.availabilitySummaryKeyboard(session));
    }

    private void renderScopeCatalog(Long chatId, Long messageId, MovieSearchSession session) {
        TorrentAvailabilityCatalog catalog = torrentAvailabilityService.catalog(session.movieMetadata());
        render(chatId, messageId, movieSearchViewFactory.availableScopesText(session, catalog), movieSearchViewFactory.availableScopesKeyboard(session, catalog));
    }

    private void renderVoiceCatalog(Long chatId, Long messageId, MovieSearchSession session) {
        TorrentAvailabilityCatalog catalog = torrentAvailabilityService.catalog(session.movieMetadata());
        render(chatId, messageId, movieSearchViewFactory.availableVoicesText(session, catalog), movieSearchViewFactory.availableVoicesKeyboard(session, catalog));
    }

    private void renderQualityCatalog(Long chatId, Long messageId, MovieSearchSession session) {
        TorrentAvailabilityCatalog catalog = torrentAvailabilityService.catalog(session.movieMetadata());
        render(chatId, messageId, movieSearchViewFactory.availableQualitiesText(session, catalog), movieSearchViewFactory.availableQualitiesKeyboard(session, catalog));
    }

    private TvSeasonDetails availableEpisodeDetails(MovieSearchSession session) {
        return availableEpisodeDetails(session, torrentAvailabilityService.catalog(session.movieMetadata()));
    }

    private TvSeasonDetails availableEpisodeDetails(MovieSearchSession session, TorrentAvailabilityCatalog catalog) {
        if (session.seasonNumber() == null) {
            return movieSearchSessionService.episodes(session);
        }
        List<TvEpisodeSummary> episodes = catalog.filtered(session.seasonNumber(), "episodes", null, null).stream()
                .flatMap(item -> item.episodeNumbers().stream())
                .distinct()
                .sorted()
                .map(number -> new TvEpisodeSummary(number, ""))
                .toList();
        if (episodes.isEmpty()) {
            return movieSearchSessionService.episodes(session);
        }
        return new TvSeasonDetails(session.seasonNumber(), "Сезон " + session.seasonNumber(), episodes);
    }

    private TorrentSearchService.SearchPage searchAvailabilityPage(MovieSearchSession session) {
        TorrentAvailabilityCatalog catalog = torrentAvailabilityService.catalog(session.movieMetadata());
        List<TorrentSearchResult> results = catalog.filtered(session.seasonNumber(), session.availabilityScope(), session.availabilityVoice(), session.availabilityQuality()).stream()
                .filter(item -> matchesSelectedEpisodes(session, item))
                .map(TorrentAvailabilityItem::result)
                .map(result -> result.withSelectionContext(session.seasonNumber(), session.episodeNumbers()))
                .toList();
        if (results.isEmpty()) {
            return torrentSearchService.searchFirstPage(session);
        }
        String query = movieSearchViewFactory.availabilityQueryLabel(session);
        String searchId = torrentSearchService.storeSearchPage(query, results);
        return torrentSearchService.findPage(searchId, 0);
    }

    private boolean matchesSelectedEpisodes(MovieSearchSession session, TorrentAvailabilityItem item) {
        if (session.episodeNumbers() == null || session.episodeNumbers().isEmpty()) {
            return true;
        }
        if (item.episodeNumbers().isEmpty()) {
            return item.containsSeason(session.seasonNumber());
        }
        for (Integer episodeNumber : session.episodeNumbers()) {
            if (item.episodeNumbers().contains(episodeNumber)) {
                return true;
            }
        }
        return false;
    }

    private void render(Long chatId, Long messageId, String text, String keyboard) {
        if (chatId == null) {
            return;
        }
        if (messageId == null) {
            telegramMessageService.sendTextWithInlineKeyboard(chatId, text, keyboard);
            return;
        }
        telegramMessageService.editText(chatId, messageId, text, keyboard);
    }
}
