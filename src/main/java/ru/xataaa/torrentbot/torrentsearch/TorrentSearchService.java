package ru.xataaa.torrentbot.torrentsearch;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.common.SafeLog;
import ru.xataaa.torrentbot.config.JacredProperties;
import ru.xataaa.torrentbot.movie.MovieMetadata;
import ru.xataaa.torrentbot.movie.MovieSearchSession;

@Service
@RequiredArgsConstructor
@Slf4j
public class TorrentSearchService {

    private static final int PAGE_SIZE = 5;
    private static final List<String> GOOD_QUALITY_MARKERS = List.of(
            "2160p", "1080p", "720p", "web-dl", "webrip", "bdrip", "hdrip", "bluray", "blu-ray"
    );
    private static final List<String> BAD_QUALITY_MARKERS = List.of(
            "camrip", "cam rip", "telesync", "tele sync", " ts ", "экран", "экранка"
    );

    private final JacredClient jacredClient;
    private final JacredProperties jacredProperties;
    private final TorrentSearchCache torrentSearchCache;
    private final FileSizeFormatter fileSizeFormatter;
    private final MeterRegistry meterRegistry;
    private final TorrentTitleParser torrentTitleParser = new TorrentTitleParser();

    public List<TorrentSearchResult> search(String userText) {
        TorrentSearchRequest request = TorrentSearchRequest.fromUserText(userText);
        return search(request);
    }

    public List<TorrentSearchResult> search(MovieMetadata movieMetadata) {
        return search(movieMetadata, TorrentSearchFilters.any());
    }

    public List<TorrentSearchResult> search(MovieSearchSession session) {
        return search(session.movieMetadata(), session.filters());
    }

    public List<TorrentSearchResult> search(MovieMetadata movieMetadata, TorrentSearchFilters filters) {
        TorrentSearchRequest request = TorrentSearchRequest.fromMovie(
                movieMetadata.title(),
                movieMetadata.originalTitle(),
                movieMetadata.year(),
                movieMetadata.isTv(),
                filters
        );
        return search(request);
    }

    public List<TorrentSearchResult> search(TorrentSearchRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        long startedAt = System.nanoTime();
        String queryHash = SafeLog.sha256Short(request.query());
        TorrentSearchFilters filters = request.filters() == null ? TorrentSearchFilters.any() : request.filters();
        log.info(
                "jacred_search_started: queryHash={}, title={}, originalTitle={}, year={}, serialType={}, quality={}, voice={}, season={}, episodes={}",
                queryHash,
                SafeLog.preview(request.title(), 40),
                SafeLog.preview(request.originalTitle(), 40),
                request.year(),
                request.serialType(),
                filters.quality().code(),
                filters.voice().code(),
                filters.seasonNumber(),
                filters.episodeNumbers()
        );
        try {
            List<JacredSearchResult> rawResults = new ArrayList<>(jacredClient.search(request, false));
            if (rawResults.size() < 3 || filters.hasSpecificFilters()) {
                rawResults.addAll(jacredClient.search(request, true));
            }

            List<TorrentSearchResult> sortedResults = rawResults.stream()
                    .map(this::toSearchResult)
                    .filter(result -> result.title() != null && !result.title().isBlank())
                    .filter(TorrentSearchResult::hasMagnet)
                    .filter(this::isAcceptableQuality)
                    .filter(result -> matchesFilters(result, filters))
                    .sorted(resultComparator())
                    .toList();

            List<TorrentSearchResult> preferredResults = removeZeroSeederResultsIfPossible(sortedResults);
            int maxResults = Math.max(PAGE_SIZE, jacredProperties.maxResults() * 10);
            List<TorrentSearchResult> results = preferredResults.stream()
                    .limit(maxResults)
                    .map(torrentSearchCache::store)
                    .toList();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            sample.stop(Timer.builder("jacred.search.duration")
                    .tag("source", "jacred")
                    .tag("result", results.isEmpty() ? "empty" : "success")
                    .tag("type", request.serialType() != null && request.serialType() == 2 ? "tv" : "movie")
                    .register(meterRegistry));
            meterRegistry.gauge("torrent.filter.result_count", results.size());
            log.info("jacred_search_completed: queryHash={}, rawResultCount={}, resultCount={}, durationMs={}",
                    queryHash, rawResults.size(), results.size(), durationMs);
            return results;
        } catch (RuntimeException runtimeException) {
            sample.stop(Timer.builder("jacred.search.duration")
                    .tag("source", "jacred")
                    .tag("result", "error")
                    .tag("type", request.serialType() != null && request.serialType() == 2 ? "tv" : "movie")
                    .register(meterRegistry));
            meterRegistry.counter("search.error", "source", "jacred", "result", "error").increment();
            log.warn("jacred_search_failed: queryHash={}, error={}", queryHash, runtimeException.getMessage());
            throw runtimeException;
        }
    }

    public SearchPage searchFirstPage(String userText) {
        List<TorrentSearchResult> results = search(userText);
        String searchId = torrentSearchCache.storeSearch(userText, results);
        return page(searchId, userText, results, 0);
    }

    public SearchPage searchFirstPage(MovieMetadata movieMetadata) {
        return searchFirstPage(movieMetadata, TorrentSearchFilters.any());
    }

    public SearchPage searchFirstPage(MovieSearchSession session) {
        List<TorrentSearchResult> results = search(session);
        String query = queryLabel(session.movieMetadata(), session.filters());
        String searchId = torrentSearchCache.storeSearch(query, results);
        return page(searchId, query, results, 0);
    }

    public SearchPage searchFirstPage(MovieMetadata movieMetadata, TorrentSearchFilters filters) {
        List<TorrentSearchResult> results = search(movieMetadata, filters);
        String query = queryLabel(movieMetadata, filters);
        String searchId = torrentSearchCache.storeSearch(query, results);
        return page(searchId, query, results, 0);
    }

    public SearchPage findPage(String searchId, int pageNumber) {
        TorrentSearchCache.SearchEntry searchEntry = torrentSearchCache.findSearch(searchId)
                .orElseThrow(() -> new IllegalArgumentException("Search result expired"));
        return page(searchId, searchEntry.query(), searchEntry.results(), pageNumber);
    }

    public String storeSearchPage(String query, List<TorrentSearchResult> results) {
        List<TorrentSearchResult> storedResults = results.stream()
                .map(torrentSearchCache::store)
                .toList();
        return torrentSearchCache.storeSearch(query, storedResults);
    }

    public String formatResultsMessage(String query, List<TorrentSearchResult> results) {
        return formatResultsMessage(query, results, 1);
    }

    public String resultsKeyboard(List<TorrentSearchResult> results) {
        return resultsKeyboard(new SearchPage("", "", results, 0, 1, results.size()));
    }

    public String resultsKeyboard(SearchPage searchPage) {
        StringBuilder builder = new StringBuilder("{\"inline_keyboard\":[");
        int index = 1;
        for (TorrentSearchResult result : searchPage.results()) {
            if (index > 1) {
                builder.append(",");
            }
            builder.append("[{\"text\":\"")
                    .append(escapeJson(buttonText(searchPage.globalStartIndex() + index, result)))
                    .append("\",\"callback_data\":\"torrent:select:")
                    .append(result.selectionId())
                    .append("\"}]");
            index++;
        }
        if (searchPage.totalPages() > 1) {
            builder.append(",[");
            if (searchPage.pageNumber() > 0) {
                builder.append("{\"text\":\"Назад\",\"callback_data\":\"torrent:page:")
                        .append(searchPage.searchId())
                        .append(":")
                        .append(searchPage.pageNumber() - 1)
                        .append("\"}");
            }
            if (searchPage.pageNumber() > 0 && searchPage.pageNumber() < searchPage.totalPages() - 1) {
                builder.append(",");
            }
            if (searchPage.pageNumber() < searchPage.totalPages() - 1) {
                builder.append("{\"text\":\"Вперёд\",\"callback_data\":\"torrent:page:")
                        .append(searchPage.searchId())
                        .append(":")
                        .append(searchPage.pageNumber() + 1)
                        .append("\"}");
            }
            builder.append("]");
        }
        builder.append(",[{\"text\":\"Новый поиск\",\"callback_data\":\"menu:search\"}]");
        builder.append("]}");
        return builder.toString();
    }

    public String formatPageMessage(SearchPage searchPage) {
        if (searchPage.totalResults() == 0) {
            return formatResultsMessage(searchPage.query(), searchPage.results());
        }
        String baseMessage = formatResultsMessage(searchPage.query(), searchPage.results(), searchPage.globalStartIndex() + 1);
        return baseMessage + "\n\nСтраница " + (searchPage.pageNumber() + 1) + " из " + searchPage.totalPages()
                + ". Всего результатов: " + searchPage.totalResults() + ".";
    }

    boolean matchesFilters(TorrentSearchResult result, TorrentSearchFilters filters) {
        TorrentSearchFilters safeFilters = filters == null ? TorrentSearchFilters.any() : filters;
        if (!safeFilters.quality().matches(result.title())) {
            return false;
        }
        if (!safeFilters.voice().matches(result.title())) {
            return false;
        }
        if (!matchesSeasonAndEpisodes(result.title(), safeFilters)) {
            return false;
        }
        return true;
    }

    String detectQuality(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String normalizedTitle = title.toLowerCase(Locale.ROOT);
        for (String qualityMarker : GOOD_QUALITY_MARKERS) {
            if (normalizedTitle.contains(qualityMarker)) {
                return qualityMarker.toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    private String formatResultsMessage(String query, List<TorrentSearchResult> results, int startIndex) {
        if (results.isEmpty()) {
            return "Ничего не нашёл по запросу: " + query
                    + "\n\nПопробуй сбросить фильтр качества/озвучки, добавить год или оригинальное название.";
        }

        StringBuilder message = new StringBuilder("Нашёл раздачи. Выбери подходящую кнопкой ниже:\n\n");
        int index = startIndex;
        for (TorrentSearchResult result : results) {
            message.append(index)
                    .append(". ")
                    .append(shorten(result.title(), 260))
                    .append("\n")
                    .append("   Сиды: ")
                    .append(result.seeders())
                    .append(", пиры: ")
                    .append(result.peers());

            if (result.sizeBytes() > 0) {
                message.append(", размер: ").append(fileSizeFormatter.format(result.sizeBytes()));
            }
            TorrentTitleParser.ParsedTorrentTitle parsed = torrentTitleParser.parse(result);
            String quality = parsed.quality();
            if (!quality.isBlank() && !"Unknown".equalsIgnoreCase(quality)) {
                message.append(", качество: ").append(quality);
            }
            if (result.tracker() != null && !result.tracker().isBlank()) {
                message.append("\n   Трекер: ").append(result.tracker());
            }
            appendOptionalDetails(message, result, parsed);
            message.append("\n\n");
            index++;
        }
        return message.toString().trim();
    }

    private SearchPage page(String searchId, String query, List<TorrentSearchResult> results, int requestedPageNumber) {
        if (results.isEmpty()) {
            return new SearchPage(searchId, query, List.of(), 0, 1, 0);
        }
        int totalPages = (int) Math.ceil(results.size() / (double) PAGE_SIZE);
        int pageNumber = Math.max(0, Math.min(requestedPageNumber, totalPages - 1));
        int fromIndex = pageNumber * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, results.size());
        return new SearchPage(searchId, query, results.subList(fromIndex, toIndex), pageNumber, totalPages, results.size());
    }

    private List<TorrentSearchResult> removeZeroSeederResultsIfPossible(List<TorrentSearchResult> results) {
        boolean hasSeededResults = results.stream().anyMatch(result -> result.seeders() > 0);
        if (!hasSeededResults) {
            return results;
        }
        return results.stream()
                .filter(result -> result.seeders() > 0)
                .toList();
    }

    private String buttonText(int index, TorrentSearchResult result) {
        TorrentTitleParser.ParsedTorrentTitle parsed = torrentTitleParser.parse(result);
        StringBuilder text = new StringBuilder("#")
                .append(index)
                .append(" · ")
                .append(buttonQuality(parsed))
                .append(" · ")
                .append(result.seeders())
                .append(" сидов");
        if (result.sizeBytes() > 0) {
            text.append(" · ").append(fileSizeFormatter.format(result.sizeBytes()));
        }
        String releaseLabel = releaseTypeLabel(parsed);
        if (!releaseLabel.isBlank()) {
            text.append(" · ").append(releaseLabel);
        }
        return shorten(text.toString(), 60);
    }

    private TorrentSearchResult toSearchResult(JacredSearchResult jacredSearchResult) {
        return new TorrentSearchResult(
                "",
                jacredSearchResult.getTitle(),
                jacredSearchResult.getTracker(),
                jacredSearchResult.getMagnetUri(),
                jacredSearchResult.getLink(),
                jacredSearchResult.getDetails(),
                jacredSearchResult.getSize() == null ? 0L : jacredSearchResult.getSize(),
                jacredSearchResult.getSeeders() == null ? 0 : jacredSearchResult.getSeeders(),
                jacredSearchResult.getPeers() == null ? 0 : jacredSearchResult.getPeers(),
                jacredSearchResult.getPublishDate()
        );
    }

    private void appendOptionalDetails(StringBuilder message, TorrentSearchResult result, TorrentTitleParser.ParsedTorrentTitle parsed) {
        if (parsed.seasonNumber() != null) {
            message.append("\n   Сезон: ").append(parsed.seasonNumber());
            if (!parsed.episodeNumbers().isEmpty()) {
                message.append(", серии: ").append(parsed.episodeNumbers());
            } else if (parsed.seasonPack()) {
                message.append(", сезон целиком");
            }
        }
        String releaseLabel = releaseTypeLabel(parsed);
        if (!releaseLabel.isBlank()) {
            message.append("\n   Тип: ").append(releaseLabel);
        }
        if (parsed.voice() != null && !parsed.voice().isBlank()) {
            message.append("\n   Озвучка: ").append(shorten(voiceLabel(parsed.voice()), 80));
        }
        if (result.publishDate() != null && !result.publishDate().isBlank()) {
            message.append("\n   Дата: ").append(shorten(result.publishDate(), 19));
        }
        if (result.details() != null && !result.details().isBlank()) {
            message.append("\n   Страница: ").append(result.details());
        }
    }

    private String buttonQuality(TorrentTitleParser.ParsedTorrentTitle parsed) {
        if (parsed.quality() == null || parsed.quality().isBlank() || "Unknown".equalsIgnoreCase(parsed.quality())) {
            return "качество не распознано";
        }
        return parsed.quality();
    }

    private String releaseTypeLabel(TorrentTitleParser.ParsedTorrentTitle parsed) {
        if (parsed.releaseType() == ReleaseType.SEASON_PACK) {
            return "сезон целиком";
        }
        if (parsed.releaseType() == ReleaseType.MULTI_SEASON_PACK) {
            return "несколько сезонов";
        }
        if (parsed.releaseType() == ReleaseType.EPISODE) {
            return "одна серия";
        }
        if (parsed.releaseType() == ReleaseType.EPISODE_RANGE) {
            return "несколько серий";
        }
        return "";
    }

    private String voiceLabel(String voice) {
        if (TorrentAvailabilityItem.UNKNOWN_VOICE.equalsIgnoreCase(voice)) {
            return "Не удалось распознать";
        }
        return voice;
    }

    private boolean isAcceptableQuality(TorrentSearchResult result) {
        String normalizedTitle = " " + result.title().toLowerCase(Locale.ROOT) + " ";
        for (String badMarker : BAD_QUALITY_MARKERS) {
            if (normalizedTitle.contains(badMarker)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesSeasonAndEpisodes(String title, TorrentSearchFilters filters) {
        if (filters.seasonNumber() == null) {
            return true;
        }
        if (title == null || title.isBlank()) {
            return false;
        }
        String normalizedTitle = title.toLowerCase(Locale.ROOT);
        if (filters.episodeNumbers().isEmpty()) {
            return containsSeason(normalizedTitle, filters.seasonNumber());
        }
        for (Integer episodeNumber : filters.episodeNumbers()) {
            if (containsEpisode(normalizedTitle, filters.seasonNumber(), episodeNumber)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSeason(String normalizedTitle, int seasonNumber) {
        String seasonTwoDigits = twoDigits(seasonNumber);
        return normalizedTitle.contains("s" + seasonTwoDigits)
                || normalizedTitle.contains(seasonNumber + " сезон")
                || normalizedTitle.contains("season " + seasonNumber);
    }

    private boolean containsEpisode(String normalizedTitle, int seasonNumber, int episodeNumber) {
        String seasonTwoDigits = twoDigits(seasonNumber);
        String episodeTwoDigits = twoDigits(episodeNumber);
        Set<String> markers = Set.of(
                "s" + seasonTwoDigits + "e" + episodeTwoDigits,
                seasonNumber + "x" + episodeNumber,
                seasonTwoDigits + "x" + episodeTwoDigits,
                seasonNumber + " сезон " + episodeNumber,
                seasonNumber + " сезон " + episodeTwoDigits,
                episodeNumber + " серия",
                episodeTwoDigits + " серия"
        );
        for (String marker : markers) {
            if (normalizedTitle.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private Comparator<TorrentSearchResult> resultComparator() {
        return Comparator
                .comparing(TorrentSearchResult::hasMagnet).reversed()
                .thenComparing(result -> result.seeders() > 0, Comparator.reverseOrder())
                .thenComparing(TorrentSearchResult::seeders, Comparator.reverseOrder())
                .thenComparing(TorrentSearchResult::peers, Comparator.reverseOrder())
                .thenComparing(TorrentSearchResult::sizeBytes, Comparator.reverseOrder());
    }

    private String queryLabel(MovieMetadata movieMetadata, TorrentSearchFilters filters) {
        StringBuilder label = new StringBuilder(movieMetadata.title());
        if (movieMetadata.year() != null) {
            label.append(" ").append(movieMetadata.year());
        }
        if (filters != null && filters.seasonNumber() != null) {
            label.append(", сезон ").append(filters.seasonNumber());
        }
        if (filters != null && !filters.episodeNumbers().isEmpty()) {
            label.append(", серии ").append(filters.episodeNumbers());
        }
        if (filters != null && filters.quality() != TorrentQuality.ANY) {
            label.append(", ").append(filters.quality().displayName());
        }
        if (filters != null && filters.voice() != VoiceFilter.ANY) {
            label.append(", ").append(filters.voice().displayName());
        }
        return label.toString();
    }

    private String twoDigits(Integer value) {
        if (value == null) {
            return "00";
        }
        return value < 10 ? "0" + value : value.toString();
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record SearchPage(
            String searchId,
            String query,
            List<TorrentSearchResult> results,
            int pageNumber,
            int totalPages,
            int totalResults
    ) {
        public int globalStartIndex() {
            return pageNumber * PAGE_SIZE;
        }
    }
}
