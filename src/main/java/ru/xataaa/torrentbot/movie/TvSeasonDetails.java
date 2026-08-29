package ru.xataaa.torrentbot.movie;

import java.util.List;

public record TvSeasonDetails(
        int seasonNumber,
        String name,
        List<TvEpisodeSummary> episodes
) {
}
