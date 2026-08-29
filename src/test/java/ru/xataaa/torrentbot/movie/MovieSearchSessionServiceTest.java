package ru.xataaa.torrentbot.movie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.config.SearchProperties;
import ru.xataaa.torrentbot.config.TmdbProperties;
import ru.xataaa.torrentbot.torrentsearch.TorrentQuality;
import ru.xataaa.torrentbot.torrentsearch.VoiceFilter;

class MovieSearchSessionServiceTest {

    @Test
    void shouldCreateSessionAndUpdateFilters() {
        MovieSearchSessionService service = new MovieSearchSessionService(
                new SearchProperties(300, 250, "ru-RU", 30, "any", "any"),
                new TmdbProperties("key", "http://tmdb", "http://img", "ru-RU", 1000, 1000, 60, 1440, 10),
                null
        );
        MovieMetadata movie = new MovieMetadata("sel", "1", MovieMediaType.TV, "Извне", "From", 2022, 8.0, "", "");

        MovieSearchSession session = service.create(movie);
        MovieSearchSession qualitySession = service.updateQuality(session.sessionId(), TorrentQuality.FULL_HD_1080P).orElseThrow();
        MovieSearchSession voiceSession = service.updateVoice(session.sessionId(), VoiceFilter.LOSTFILM).orElseThrow();
        MovieSearchSession seasonSession = service.selectSeason(session.sessionId(), 2).orElseThrow();
        MovieSearchSession episodeSession = service.toggleEpisode(session.sessionId(), 3).orElseThrow();

        assertThat(qualitySession.quality()).isEqualTo(TorrentQuality.FULL_HD_1080P);
        assertThat(voiceSession.voice()).isEqualTo(VoiceFilter.LOSTFILM);
        assertThat(seasonSession.seasonNumber()).isEqualTo(2);
        assertThat(episodeSession.episodeNumbers()).containsExactly(3);
    }
}
