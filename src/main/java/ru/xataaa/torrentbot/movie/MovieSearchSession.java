package ru.xataaa.torrentbot.movie;

import java.time.Instant;
import java.util.Set;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchFilters;
import ru.xataaa.torrentbot.torrentsearch.TorrentQuality;
import ru.xataaa.torrentbot.torrentsearch.VoiceFilter;

public record MovieSearchSession(
        String sessionId,
        MovieMetadata movieMetadata,
        TorrentQuality quality,
        VoiceFilter voice,
        String availabilityVoice,
        String availabilityQuality,
        String availabilityScope,
        Integer seasonNumber,
        Set<Integer> episodeNumbers,
        Instant expiresAt
) {
    public TorrentSearchFilters filters() {
        return new TorrentSearchFilters(quality, voice, seasonNumber, episodeNumbers);
    }
}
