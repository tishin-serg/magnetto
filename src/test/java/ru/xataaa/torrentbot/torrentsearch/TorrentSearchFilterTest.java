package ru.xataaa.torrentbot.torrentsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TorrentSearchFilterTest {

    private final TorrentSearchService torrentSearchService = new TorrentSearchService(null, null, null, null, null);

    @Test
    void shouldMatchSelectedQuality() {
        TorrentSearchResult result = result("The Matrix 1999 BluRay 1080p DUB.mkv");
        TorrentSearchFilters filters = new TorrentSearchFilters(TorrentQuality.FULL_HD_1080P, VoiceFilter.ANY, null, Set.of());

        assertThat(torrentSearchService.matchesFilters(result, filters)).isTrue();
    }

    @Test
    void shouldRejectDifferentQuality() {
        TorrentSearchResult result = result("The Matrix 1999 BluRay 720p DUB.mkv");
        TorrentSearchFilters filters = new TorrentSearchFilters(TorrentQuality.FULL_HD_1080P, VoiceFilter.ANY, null, Set.of());

        assertThat(torrentSearchService.matchesFilters(result, filters)).isFalse();
    }

    @Test
    void shouldMatchVoiceMarker() {
        TorrentSearchResult result = result("Извне From S02E03 WEB-DL 1080p LostFilm.mkv");
        TorrentSearchFilters filters = new TorrentSearchFilters(TorrentQuality.ANY, VoiceFilter.LOSTFILM, null, Set.of());

        assertThat(torrentSearchService.matchesFilters(result, filters)).isTrue();
    }

    @Test
    void shouldMatchSelectedEpisode() {
        TorrentSearchResult result = result("From S02E03 WEB-DL 1080p LostFilm.mkv");
        TorrentSearchFilters filters = new TorrentSearchFilters(TorrentQuality.ANY, VoiceFilter.ANY, 2, Set.of(3));

        assertThat(torrentSearchService.matchesFilters(result, filters)).isTrue();
    }

    private TorrentSearchResult result(String title) {
        return new TorrentSearchResult("id", title, "tracker", "magnet:?xt=urn:btih:test", "", "", 100L, 10, 1, "");
    }
}
