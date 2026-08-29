package ru.xataaa.torrentbot.torrentsearch;

import java.util.Set;
import java.util.TreeSet;

public record TorrentSearchResult(
        String selectionId,
        String title,
        String tracker,
        String magnetUri,
        String link,
        String details,
        long sizeBytes,
        int seeders,
        int peers,
        String publishDate,
        Integer selectedSeasonNumber,
        Set<Integer> selectedEpisodeNumbers
) {
    public TorrentSearchResult(
            String selectionId,
            String title,
            String tracker,
            String magnetUri,
            String link,
            String details,
            long sizeBytes,
            int seeders,
            int peers,
            String publishDate
    ) {
        this(selectionId, title, tracker, magnetUri, link, details, sizeBytes, seeders, peers, publishDate, null, Set.of());
    }

    public TorrentSearchResult {
        selectedEpisodeNumbers = selectedEpisodeNumbers == null ? Set.of() : Set.copyOf(new TreeSet<>(selectedEpisodeNumbers));
    }

    public boolean hasMagnet() {
        return magnetUri != null && !magnetUri.isBlank();
    }

    public TorrentSearchResult withSelectionContext(Integer seasonNumber, Set<Integer> episodeNumbers) {
        return new TorrentSearchResult(
                selectionId,
                title,
                tracker,
                magnetUri,
                link,
                details,
                sizeBytes,
                seeders,
                peers,
                publishDate,
                seasonNumber,
                episodeNumbers
        );
    }
}
