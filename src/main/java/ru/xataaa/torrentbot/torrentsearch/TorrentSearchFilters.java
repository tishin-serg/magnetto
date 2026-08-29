package ru.xataaa.torrentbot.torrentsearch;

import java.util.Set;
import java.util.TreeSet;

public record TorrentSearchFilters(
        TorrentQuality quality,
        VoiceFilter voice,
        Integer seasonNumber,
        Set<Integer> episodeNumbers
) {
    public TorrentSearchFilters {
        quality = quality == null ? TorrentQuality.ANY : quality;
        voice = voice == null ? VoiceFilter.ANY : voice;
        episodeNumbers = episodeNumbers == null ? Set.of() : Set.copyOf(new TreeSet<>(episodeNumbers));
    }

    public static TorrentSearchFilters any() {
        return new TorrentSearchFilters(TorrentQuality.ANY, VoiceFilter.ANY, null, Set.of());
    }

    public boolean hasSpecificFilters() {
        return quality != TorrentQuality.ANY
                || voice != VoiceFilter.ANY
                || seasonNumber != null
                || !episodeNumbers.isEmpty();
    }

    public boolean hasEpisodeSelection() {
        return seasonNumber != null && !episodeNumbers.isEmpty();
    }
}
