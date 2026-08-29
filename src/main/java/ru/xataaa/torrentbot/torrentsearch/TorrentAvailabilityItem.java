package ru.xataaa.torrentbot.torrentsearch;

import java.util.Set;

public record TorrentAvailabilityItem(
        TorrentSearchResult result,
        SeasonRange seasonRange,
        EpisodeRange episodeRange,
        ReleaseType releaseType,
        String quality,
        String voice
) {
    public static final String UNKNOWN_VOICE = "Озвучка не указана";

    public TorrentAvailabilityItem {
        releaseType = releaseType == null ? ReleaseType.UNKNOWN : releaseType;
        quality = quality == null || quality.isBlank() ? "Unknown" : quality.trim();
        voice = voice == null || voice.isBlank() ? UNKNOWN_VOICE : voice.trim();
    }

    public Integer seasonNumber() {
        return seasonRange == null || seasonRange.multiSeason() ? null : seasonRange.start();
    }

    public Set<Integer> seasonNumbers() {
        return seasonRange == null ? Set.of() : seasonRange.seasons();
    }

    public Set<Integer> episodeNumbers() {
        return episodeRange == null ? Set.of() : episodeRange.episodes();
    }

    public boolean seasonPack() {
        return releaseType == ReleaseType.SEASON_PACK;
    }

    public boolean multiSeasonPack() {
        return releaseType == ReleaseType.MULTI_SEASON_PACK;
    }

    public boolean containsSeason(Integer seasonNumber) {
        return seasonNumber == null || seasonRange == null || seasonRange.contains(seasonNumber);
    }

    public int seeders() {
        return result == null ? 0 : result.seeders();
    }

    public String publishDate() {
        return result == null ? "" : result.publishDate();
    }
}
