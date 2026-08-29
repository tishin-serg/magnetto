package ru.xataaa.torrentbot.torrentsearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TorrentTitleParserTest {

    private final TorrentTitleParser parser = new TorrentTitleParser();

    @Test
    void shouldParseSeasonPackWithEpisodeRange() {
        TorrentTitleParser.ParsedTorrentTitle parsed = parser.parse(result("Show / 1 сезон: 1-12 серии из 12 / ПМ (TVShows) / HDRip"));

        assertThat(parsed.seasonRange()).isEqualTo(new SeasonRange(1, 1));
        assertThat(parsed.episodeRange()).isEqualTo(new EpisodeRange(1, 12));
        assertThat(parsed.releaseType()).isEqualTo(ReleaseType.SEASON_PACK);
        assertThat(parsed.quality()).isEqualTo("HDRip");
        assertThat(parsed.voice()).isEqualTo("TVShows");
    }

    @Test
    void shouldParseMultiSeasonPack() {
        TorrentTitleParser.ParsedTorrentTitle parsed = parser.parse(result("Show / 1-5 сезон: 1-56 серии из 56 / АП (Сербин) / HDRip"));

        assertThat(parsed.seasonRange()).isEqualTo(new SeasonRange(1, 5));
        assertThat(parsed.episodeRange()).isEqualTo(new EpisodeRange(1, 56));
        assertThat(parsed.releaseType()).isEqualTo(ReleaseType.MULTI_SEASON_PACK);
        assertThat(parsed.voice()).isEqualTo("Сербин");
    }

    @Test
    void shouldParseSxxSeasonRange() {
        TorrentTitleParser.ParsedTorrentTitle parsed = parser.parse(result("Show [S01-S03] 1080p MVO (LostFilm) + Original"));

        assertThat(parsed.seasonRange()).isEqualTo(new SeasonRange(1, 3));
        assertThat(parsed.releaseType()).isEqualTo(ReleaseType.MULTI_SEASON_PACK);
        assertThat(parsed.voice()).isEqualTo("LostFilm + Original");
    }

    @Test
    void shouldParseEpisodeFormatsWithoutConfusingEpisodeNumbers() {
        TorrentTitleParser.ParsedTorrentTitle sxxExx = parser.parse(result("Show S02E07 WEB-DL"));
        TorrentTitleParser.ParsedTorrentTitle xFormat = parser.parse(result("Show 2x07 WEBRip"));
        TorrentTitleParser.ParsedTorrentTitle otherEpisode = parser.parse(result("Show S02E17 WEB-DL"));

        assertThat(sxxExx.seasonNumber()).isEqualTo(2);
        assertThat(sxxExx.episodeNumbers()).containsExactly(7);
        assertThat(sxxExx.releaseType()).isEqualTo(ReleaseType.EPISODE);
        assertThat(xFormat.seasonNumber()).isEqualTo(2);
        assertThat(xFormat.episodeNumbers()).containsExactly(7);
        assertThat(otherEpisode.episodeNumbers()).contains(17);
        assertThat(otherEpisode.episodeNumbers()).doesNotContain(7);
    }

    @Test
    void shouldParseExplicitSeasonAndEpisodeRange() {
        TorrentTitleParser.ParsedTorrentTitle parsed = parser.parse(result("Сезон: 3 / Серии: 1-12 [2012, США, Драма, BDRip] MVO (LostFilm) + Original"));

        assertThat(parsed.seasonRange()).isEqualTo(new SeasonRange(3, 3));
        assertThat(parsed.episodeRange()).isEqualTo(new EpisodeRange(1, 12));
        assertThat(parsed.releaseType()).isEqualTo(ReleaseType.SEASON_PACK);
        assertThat(parsed.quality()).isEqualTo("BDRip");
        assertThat(parsed.voice()).isEqualTo("LostFilm + Original");
    }

    @Test
    void shouldNotUseYearsOrTitlesAsVoice() {
        TorrentTitleParser.ParsedTorrentTitle parsed = parser.parse(result(
                "Подпольная Империя (Преступная империя) (1-5 сезон: 1-56 серии из 56) / Boardwalk Empire / 2010-2014 / HDRip"));

        assertThat(parsed.voice()).isEqualTo(TorrentAvailabilityItem.UNKNOWN_VOICE);
    }

    @Test
    void shouldExtractVoiceStudiosFromBlackMirrorSeasonPackTitle() {
        TorrentTitleParser.ParsedTorrentTitle parsed = parser.parse(result(
                "Черное зеркало (3 сезон: 1-6 серии из 6) / Black Mirror / 2016 / "
                        + "ДБ (Пифагор), СТ / 4K, HEVC, HDR, Dolby Vision / WEB-DL (2160p) "
                        + "| Дубляж | Пифагор | BTI Studios"));

        assertThat(parsed.seasonRange()).isEqualTo(new SeasonRange(3, 3));
        assertThat(parsed.episodeRange()).isEqualTo(new EpisodeRange(1, 6));
        assertThat(parsed.releaseType()).isEqualTo(ReleaseType.SEASON_PACK);
        assertThat(parsed.quality()).isEqualTo("WEB-DL 2160p");
        assertThat(parsed.voice()).contains("Пифагор", "BTI Studios", "Дубляж", "Субтитры");
        assertThat(parsed.voice()).doesNotContain(TorrentAvailabilityItem.UNKNOWN_VOICE);
    }

    private TorrentSearchResult result(String title) {
        return new TorrentSearchResult("id", title, "tracker", "magnet:?xt=urn:btih:test", "", "", 100L, 10, 1, "");
    }
}
