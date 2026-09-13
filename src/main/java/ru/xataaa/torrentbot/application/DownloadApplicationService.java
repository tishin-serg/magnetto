package ru.xataaa.torrentbot.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.job.DownloadJobService;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.movie.MovieMetadata;
import ru.xataaa.torrentbot.movie.MovieMetadataService;
import ru.xataaa.torrentbot.preferences.DownloadPreferences;
import ru.xataaa.torrentbot.torrentsearch.*;

@Service
@RequiredArgsConstructor
public class DownloadApplicationService {
    private final MovieMetadataService movies;
    private final TorrentSearchService torrents;
    private final TorrentSearchCache selectionCache;
    private final DownloadJobService jobs;

    public List<MovieMetadata> searchCatalog(String query) { return movies.search(query); }

    public List<TorrentSearchResult> searchTorrents(String selectionId, Integer season, Set<Integer> episodes,
                                                     String quality, String voice) {
        MovieMetadata movie = movies.findBySelectionId(selectionId).orElseThrow(() -> new SelectionExpiredException("selectionId expired"));
        TorrentSearchFilters filters = new TorrentSearchFilters(TorrentQuality.fromCode(quality), VoiceFilter.fromCode(voice), season, episodes);
        return torrents.search(movie, filters);
    }

    public UUID create(Long chatId, String movieSelectionId, String torrentSelectionId, Integer season,
                       Set<Integer> episodes, String quality, String voice, DeliveryTarget deliveryTarget,
                       boolean automatic, ShortcutPreferences shortcutPreferences) {
        MovieMetadata movie = movies.findBySelectionId(movieSelectionId).orElseThrow(() -> new SelectionExpiredException("selectionId expired"));
        ShortcutPreferences profile = shortcutPreferences == null ? ShortcutPreferences.defaults() : shortcutPreferences;
        String effectiveQuality = quality == null || quality.isBlank() ? profile.quality() : quality;
        String effectiveVoice = voice == null || voice.isBlank() ? profile.voice() : voice;
        TorrentSearchFilters filters = new TorrentSearchFilters(TorrentQuality.fromCode(effectiveQuality), VoiceFilter.fromCode(effectiveVoice), season, episodes);
        List<TorrentSearchResult> results = torrents.search(movie, filters);
        TorrentSearchResult selected = torrentSelectionId == null || torrentSelectionId.isBlank()
                ? results.stream().filter(r -> accepts(r, profile)).filter(r -> r.sizeBytes() >= profile.minBytes() && r.sizeBytes() <= profile.maxBytes() && r.seeders() >= profile.minSeeders()).findFirst().orElseThrow(() -> new NoAutomaticTorrentException("No suitable torrent"))
                : selectionCache.find(torrentSelectionId).orElseThrow(() -> new SelectionExpiredException("selectionId expired"));
        DownloadTarget target = switch (deliveryTarget) {
            case HOME_LIBRARY -> DownloadTarget.HOME_PC;
            case S3_LIBRARY, PHONE_S3_TEMP -> DownloadTarget.S3;
            case TELEGRAM_OR_WEBDAV, PHONE_VPS_TEMP -> DownloadTarget.VPS;
        };
        DownloadPreferences preferences = new DownloadPreferences(profile.minBytes(), profile.maxBytes(), profile.minSeeders(), target, profile.minSpeedBytesPerSecond(), profile.autoReplaceSlowDownload());
        return jobs.startDownload(UUID.randomUUID(), chatId, selected.magnetUri(), selected.sizeBytes(), target,
                selected.title(), preferences, results);
    }

    private boolean accepts(TorrentSearchResult result, ShortcutPreferences profile) {
        return result.sizeBytes() > 0 && TorrentQuality.fromCode(profile.quality()).matches(result.title())
                && VoiceFilter.fromCode(profile.voice()).matches(result.title());
    }

    public static class SelectionExpiredException extends RuntimeException { public SelectionExpiredException(String m) { super(m); } }
    public static class NoAutomaticTorrentException extends RuntimeException { public NoAutomaticTorrentException(String m) { super(m); } }
}
