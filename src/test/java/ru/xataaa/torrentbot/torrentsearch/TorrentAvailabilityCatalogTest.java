package ru.xataaa.torrentbot.torrentsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.movie.MovieMediaType;
import ru.xataaa.torrentbot.movie.MovieMetadata;

class TorrentAvailabilityCatalogTest {

    @Test
    void shouldBuildAvailableSeasonsVoicesAndQualities() {
        MovieMetadata movie = new MovieMetadata("sel", "tmdb", MovieMediaType.TV, "Show", "Show", 2024, 0.0, "", "");
        TorrentAvailabilityCatalog catalog = new TorrentAvailabilityCatalog(movie, List.of(
                item("Show S02 Complete 1080p [LostFilm]", new SeasonRange(2, 2), null, ReleaseType.SEASON_PACK, "LostFilm", "1080p", 30),
                item("Show S03 Complete 720p [NewStudio]", new SeasonRange(3, 3), null, ReleaseType.SEASON_PACK, "NewStudio", "720p", 5)
        ));

        assertThat(catalog.seasonOptions()).extracting(TorrentAvailabilityCatalog.SeasonOption::seasonNumber)
                .containsExactly(2, 3);
        assertThat(catalog.scopeOptions(2)).extracting(TorrentAvailabilityCatalog.ScopeOption::code)
                .containsExactly("season-pack", "episodes");
        assertThat(catalog.voiceOptions(2, "season-pack")).extracting(TorrentAvailabilityCatalog.VoiceOption::voice)
                .containsExactly(TorrentAvailabilityCatalog.ANY_VOICE, "LostFilm");
        assertThat(catalog.qualityOptions(2, "season-pack", "LostFilm")).extracting(TorrentAvailabilityCatalog.QualityOption::quality)
                .contains(TorrentAvailabilityCatalog.ANY_QUALITY, TorrentAvailabilityCatalog.QUALITY_SMALL, "1080p");
        assertThat(catalog.filtered(2, "season-pack", "LostFilm", "1080p")).hasSize(1);
        assertThat(catalog.filtered(2, "season-pack", "NewStudio", "720p")).isEmpty();
        assertThat(catalog.filtered(2, "season-pack", TorrentAvailabilityCatalog.ANY_VOICE, TorrentAvailabilityCatalog.ANY_QUALITY)).hasSize(1);
    }

    @Test
    void shouldAttachMultiSeasonPackToEverySeasonButKeepItBelowExactSeasonPack() {
        MovieMetadata movie = new MovieMetadata("sel", "tmdb", MovieMediaType.TV, "Show", "Show", 2024, 0.0, "", "");
        TorrentAvailabilityCatalog catalog = new TorrentAvailabilityCatalog(movie, List.of(
                item("Show S01 Complete", new SeasonRange(1, 1), new EpisodeRange(1, 12), ReleaseType.SEASON_PACK, "LostFilm", "1080p", 10),
                item("Show S01-S03", new SeasonRange(1, 3), new EpisodeRange(1, 36), ReleaseType.MULTI_SEASON_PACK, "LostFilm", "1080p", 50)
        ));

        assertThat(catalog.seasonOptions()).extracting(TorrentAvailabilityCatalog.SeasonOption::seasonNumber)
                .containsExactly(1, 2, 3);
        assertThat(catalog.multiSeasonPackCount()).isEqualTo(1);
        assertThat(catalog.scopeOptions(1)).extracting(TorrentAvailabilityCatalog.ScopeOption::code)
                .containsExactly("season-pack", "episodes", "multi-season");
        assertThat(catalog.filtered(1, null, "LostFilm", "1080p")).extracting(TorrentAvailabilityItem::releaseType)
                .containsExactly(ReleaseType.SEASON_PACK, ReleaseType.MULTI_SEASON_PACK);
    }

    @Test
    void shouldGroupUnknownVoices() {
        MovieMetadata movie = new MovieMetadata("sel", "tmdb", MovieMediaType.MOVIE, "Movie", "Movie", 2024, 0.0, "", "");
        TorrentAvailabilityCatalog catalog = new TorrentAvailabilityCatalog(movie, List.of(
                item("Movie 1080p", null, null, ReleaseType.MOVIE, TorrentAvailabilityItem.UNKNOWN_VOICE, "1080p", 5),
                item("Movie 720p", null, null, ReleaseType.MOVIE, TorrentAvailabilityItem.UNKNOWN_VOICE, "720p", 2)
        ));

        assertThat(catalog.voiceOptions(null)).extracting(TorrentAvailabilityCatalog.VoiceOption::voice)
                .containsExactly(TorrentAvailabilityCatalog.ANY_VOICE, TorrentAvailabilityItem.UNKNOWN_VOICE);
    }

    @Test
    void shouldExposeAggregatedVoiceTokensInsteadOfCompositeVoiceStrings() {
        MovieMetadata movie = new MovieMetadata("sel", "tmdb", MovieMediaType.TV, "Show", "Show", 2024, 0.0, "", "");
        TorrentAvailabilityCatalog catalog = new TorrentAvailabilityCatalog(movie, List.of(
                item("Show S03 videofilm", new SeasonRange(3, 3), null, ReleaseType.SEASON_PACK,
                        "Videofilm Int. + \u0414\u0443\u0431\u043b\u044f\u0436 + \u0421\u0443\u0431\u0442\u0438\u0442\u0440\u044b", "1080p", 700),
                item("Show S03 lostfilm mixed", new SeasonRange(3, 3), null, ReleaseType.SEASON_PACK,
                        "\u0414\u0443\u0431\u043b\u044f\u0436 + \u0421\u0443\u0431\u0442\u0438\u0442\u0440\u044b + LostFilm + SomeStudio", "1080p", 70),
                item("Show S03 lostfilm subs", new SeasonRange(3, 3), null, ReleaseType.SEASON_PACK,
                        "Lost Film + \u0421\u0443\u0431\u0442\u0438\u0442\u0440\u044b", "1080p", 69),
                item("Show S03 ukr eng", new SeasonRange(3, 3), null, ReleaseType.SEASON_PACK,
                        "Ukr + Eng", "1080p", 8),
                item("Show S03 unknown", new SeasonRange(3, 3), null, ReleaseType.SEASON_PACK,
                        TorrentAvailabilityItem.UNKNOWN_VOICE, "1080p", 1)
        ));

        assertThat(catalog.voiceOptions(3, "season-pack"))
                .extracting(TorrentAvailabilityCatalog.VoiceOption::voice)
                .containsSubsequence(
                        TorrentAvailabilityCatalog.ANY_VOICE,
                        "Videofilm",
                        "\u0414\u0443\u0431\u043b\u044f\u0436",
                        "LostFilm",
                        "Ukr/Eng",
                        TorrentAvailabilityItem.UNKNOWN_VOICE
                );
        assertThat(catalog.filtered(3, "season-pack", "LostFilm", "1080p"))
                .extracting(TorrentAvailabilityItem::seeders)
                .containsExactly(70, 69);
    }

    @Test
    void shouldFilterVoiceByStudioInclusion() {
        MovieMetadata movie = new MovieMetadata("sel", "tmdb", MovieMediaType.TV, "Show", "Show", 2024, 0.0, "", "");
        TorrentAvailabilityCatalog catalog = new TorrentAvailabilityCatalog(movie, List.of(
                item("Show S03 LostFilm mixed", new SeasonRange(3, 3), null, ReleaseType.SEASON_PACK,
                        "Дубляж + Субтитры + LostFilm + SomeStudio", "1080p", 70),
                item("Show S03 LostFilm subs", new SeasonRange(3, 3), null, ReleaseType.SEASON_PACK,
                        "LostFilm + Субтитры", "1080p", 69),
                item("Show S03 dub only", new SeasonRange(3, 3), null, ReleaseType.SEASON_PACK,
                        "Дубляж + Субтитры", "1080p", 17)
        ));

        assertThat(catalog.filtered(3, "season-pack", "LostFilm + Субтитры", "1080p"))
                .extracting(TorrentAvailabilityItem::seeders)
                .containsExactly(70, 69);
        assertThat(catalog.hasVoice("LostFilm + Субтитры", 3, "season-pack")).isTrue();
    }

    @Test
    void shouldExposeHumanQualityGroups() {
        MovieMetadata movie = new MovieMetadata("sel", "tmdb", MovieMediaType.TV, "Show", "Show", 2024, 0.0, "", "");
        TorrentAvailabilityCatalog catalog = new TorrentAvailabilityCatalog(movie, List.of(
                item("Show S03 WEB-DL 1080p", new SeasonRange(3, 3), null, ReleaseType.SEASON_PACK, "LostFilm", "WEB-DL 1080p", 30),
                item("Show S03 WEB-DL 2160p", new SeasonRange(3, 3), null, ReleaseType.SEASON_PACK, "LostFilm", "WEB-DL 2160p", 20),
                item("Show S03 BDRemux", new SeasonRange(3, 3), null, ReleaseType.SEASON_PACK, "LostFilm", "BDRemux", 10)
        ));

        assertThat(catalog.qualityOptions(3, "season-pack", "LostFilm"))
                .extracting(TorrentAvailabilityCatalog.QualityOption::quality)
                .containsSubsequence(
                        TorrentAvailabilityCatalog.ANY_QUALITY,
                        TorrentAvailabilityCatalog.QUALITY_OPTIMAL,
                        TorrentAvailabilityCatalog.QUALITY_SMALL,
                        TorrentAvailabilityCatalog.QUALITY_MAX
                );
        assertThat(catalog.filtered(3, "season-pack", "LostFilm", TorrentAvailabilityCatalog.QUALITY_OPTIMAL))
                .extracting(TorrentAvailabilityItem::quality)
                .containsExactly("WEB-DL 1080p");
        assertThat(catalog.filtered(3, "season-pack", "LostFilm", TorrentAvailabilityCatalog.QUALITY_MAX))
                .extracting(TorrentAvailabilityItem::quality)
                .containsExactly("WEB-DL 2160p", "BDRemux");
    }

    @Test
    void shouldSortBySeedersThenDate() {
        MovieMetadata movie = new MovieMetadata("sel", "tmdb", MovieMediaType.MOVIE, "Movie", "Movie", 2024, 0.0, "", "");
        TorrentAvailabilityCatalog catalog = new TorrentAvailabilityCatalog(movie, List.of(
                item("Movie 1080p [A]", null, null, ReleaseType.MOVIE, "A", "1080p", 5, "2024-01-01T00:00:00Z"),
                item("Movie 1080p [A]", null, null, ReleaseType.MOVIE, "A", "1080p", 20, "2023-01-01T00:00:00Z"),
                item("Movie 1080p [A]", null, null, ReleaseType.MOVIE, "A", "1080p", 5, "2025-01-01T00:00:00Z")
        ));

        assertThat(catalog.filtered(null, "A", "1080p")).extracting(TorrentAvailabilityItem::seeders)
                .containsExactly(20, 5, 5);
        assertThat(catalog.filtered(null, "A", "1080p").get(1).publishDate()).startsWith("2025");
    }

    private TorrentAvailabilityItem item(String title, SeasonRange seasonRange, EpisodeRange episodeRange,
                                         ReleaseType releaseType, String voice, String quality, int seeders) {
        return item(title, seasonRange, episodeRange, releaseType, voice, quality, seeders, "2024-01-01T00:00:00Z");
    }

    private TorrentAvailabilityItem item(String title, SeasonRange seasonRange, EpisodeRange episodeRange,
                                         ReleaseType releaseType, String voice, String quality, int seeders, String publishDate) {
        return new TorrentAvailabilityItem(
                new TorrentSearchResult("id", title, "tracker", "magnet:?xt=urn:btih:" + title.hashCode(), "", "", 100L, seeders, 1, publishDate),
                seasonRange,
                episodeRange,
                releaseType,
                quality,
                voice
        );
    }
}
