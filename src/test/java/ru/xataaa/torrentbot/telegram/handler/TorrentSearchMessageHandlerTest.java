package ru.xataaa.torrentbot.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.config.AppProperties;
import ru.xataaa.torrentbot.config.TelegramProperties;
import ru.xataaa.torrentbot.movie.MovieMediaType;
import ru.xataaa.torrentbot.movie.MovieMetadata;
import ru.xataaa.torrentbot.movie.MovieMetadataService;
import ru.xataaa.torrentbot.telegram.TelegramInlineResultFactory;
import ru.xataaa.torrentbot.telegram.TelegramKeyboardFactory;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchService;

class TorrentSearchMessageHandlerTest {

    @Test
    void shouldRouteSearchCommandDirectlyToTorrentSearch() {
        TorrentSearchService torrentSearchService = mock(TorrentSearchService.class);
        MovieMetadataService movieMetadataService = mock(MovieMetadataService.class);
        TelegramMessageService telegramMessageService = mock(TelegramMessageService.class);
        TorrentSearchService.SearchPage emptyPage = emptyPage("zxqv-no-tmdb-match-884421");
        when(torrentSearchService.searchFirstPage("zxqv-no-tmdb-match-884421")).thenReturn(emptyPage);
        when(torrentSearchService.formatPageMessage(emptyPage)).thenReturn("Ничего не нашёл");

        TorrentSearchMessageHandler handler = handler(torrentSearchService, movieMetadataService, telegramMessageService);

        handler.handle(42L, "/search zxqv-no-tmdb-match-884421");

        verify(telegramMessageService).sendTyping(42L);
        verify(torrentSearchService).searchFirstPage("zxqv-no-tmdb-match-884421");
        verify(movieMetadataService, never()).search("zxqv-no-tmdb-match-884421");
        verify(telegramMessageService).sendText(42L, "Ничего не нашёл");
    }

    @Test
    void shouldRoutePlainTextDirectlyToTorrentSearchWhenTmdbCacheIsEmpty() {
        TorrentSearchService torrentSearchService = mock(TorrentSearchService.class);
        MovieMetadataService movieMetadataService = mock(MovieMetadataService.class);
        TelegramMessageService telegramMessageService = mock(TelegramMessageService.class);
        TorrentSearchService.SearchPage emptyPage = emptyPage("Матрица");
        when(torrentSearchService.searchFirstPage("Матрица")).thenReturn(emptyPage);
        when(torrentSearchService.formatPageMessage(emptyPage)).thenReturn("Ничего не нашёл");
        when(movieMetadataService.findCached("Матрица")).thenReturn(Optional.empty());

        TorrentSearchMessageHandler handler = handler(torrentSearchService, movieMetadataService, telegramMessageService);

        long startedAt = System.nanoTime();
        handler.handle(42L, "Матрица");
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(elapsedMs).isLessThan(2_500L);
        verify(movieMetadataService).findCached("Матрица");
        verify(movieMetadataService, never()).search("Матрица");
        verify(torrentSearchService).searchFirstPage("Матрица");
        verify(telegramMessageService).sendText(42L, "Ничего не нашёл");
    }

    @Test
    void shouldShowCachedMovieCandidatesForPlainTextWithoutExternalTmdbSearch() {
        TorrentSearchService torrentSearchService = mock(TorrentSearchService.class);
        MovieMetadataService movieMetadataService = mock(MovieMetadataService.class);
        TelegramMessageService telegramMessageService = mock(TelegramMessageService.class);
        TelegramInlineResultFactory inlineResultFactory = mock(TelegramInlineResultFactory.class);
        List<MovieMetadata> cachedMovies = List.of(new MovieMetadata(
                "selection-id",
                "603",
                MovieMediaType.MOVIE,
                "Матрица",
                "The Matrix",
                1999,
                8.2,
                "",
                ""
        ));
        when(movieMetadataService.findCached("Матрица")).thenReturn(Optional.of(cachedMovies));
        when(inlineResultFactory.movieCandidatesText("Матрица", cachedMovies)).thenReturn("Выбери фильм");
        when(inlineResultFactory.movieCandidatesKeyboard(cachedMovies)).thenReturn("[]");

        TorrentSearchMessageHandler handler = new TorrentSearchMessageHandler(
                torrentSearchService,
                movieMetadataService,
                inlineResultFactory,
                mock(TelegramKeyboardFactory.class),
                telegramMessageService,
                new AppProperties(100, 100, 100, "", true, 30, 24, 3, 30, 48),
                new TelegramProperties("token", "magnettto_bot", "http://telegram", 1000, 1000, 1000,
                        new TelegramProperties.FileProperties(100, 24, true))
        );

        handler.handle(42L, "Матрица");

        verify(movieMetadataService).findCached("Матрица");
        verify(movieMetadataService, never()).search("Матрица");
        verify(torrentSearchService, never()).searchFirstPage("Матрица");
        verify(telegramMessageService).sendTextWithInlineKeyboard(42L, "Выбери фильм", "[]");
    }
    private TorrentSearchMessageHandler handler(
            TorrentSearchService torrentSearchService,
            MovieMetadataService movieMetadataService,
            TelegramMessageService telegramMessageService
    ) {
        return new TorrentSearchMessageHandler(
                torrentSearchService,
                movieMetadataService,
                mock(TelegramInlineResultFactory.class),
                mock(TelegramKeyboardFactory.class),
                telegramMessageService,
                new AppProperties(100, 100, 100, "", true, 30, 24, 3, 30, 48),
                new TelegramProperties("token", "magnettto_bot", "http://telegram", 1000, 1000, 1000,
                        new TelegramProperties.FileProperties(100, 24, true))
        );
    }

    private TorrentSearchService.SearchPage emptyPage(String query) {
        return new TorrentSearchService.SearchPage(
                "search-id",
                query,
                List.of(),
                0,
                1,
                0
        );
    }
}