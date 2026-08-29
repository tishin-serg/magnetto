package ru.xataaa.torrentbot.torrentsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TorrentSearchRequestTest {

    @Test
    void shouldExtractYearFromMovieQuery() {
        TorrentSearchRequest request = TorrentSearchRequest.fromUserText("матрица 1999");

        assertThat(request.title()).isEqualTo("матрица");
        assertThat(request.year()).isEqualTo(1999);
        assertThat(request.serialType()).isEqualTo(1);
    }

    @Test
    void shouldDetectSerialQuery() {
        TorrentSearchRequest request = TorrentSearchRequest.fromUserText("сериал lost 2004");

        assertThat(request.year()).isEqualTo(2004);
        assertThat(request.serialType()).isEqualTo(2);
    }

    @Test
    void shouldBuildStructuredRequestFromMovieCard() {
        TorrentSearchRequest request = TorrentSearchRequest.fromMovie("Матрица", "The Matrix", 1999, false);

        assertThat(request.title()).isEqualTo("Матрица");
        assertThat(request.originalTitle()).isEqualTo("The Matrix");
        assertThat(request.year()).isEqualTo(1999);
        assertThat(request.serialType()).isEqualTo(1);
        assertThat(request.query()).isEqualTo("Матрица 1999");
    }

    @Test
    void shouldBuildStructuredRequestFromTvCard() {
        TorrentSearchRequest request = TorrentSearchRequest.fromMovie("Футурама", "Futurama", 1999, true);

        assertThat(request.title()).isEqualTo("Футурама");
        assertThat(request.originalTitle()).isEqualTo("Futurama");
        assertThat(request.year()).isEqualTo(1999);
        assertThat(request.serialType()).isEqualTo(2);
    }

    @Test
    void shouldAddEpisodePatternToStructuredQuery() {
        TorrentSearchFilters filters = new TorrentSearchFilters(
                TorrentQuality.ANY,
                VoiceFilter.ANY,
                2,
                Set.of(3)
        );

        TorrentSearchRequest request = TorrentSearchRequest.fromMovie("Извне", "From", 2022, true, filters);

        assertThat(request.query()).contains("S02E03");
    }
}
