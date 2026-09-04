package ru.xataaa.torrentbot.telegram;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.config.SearchProperties;
import ru.xataaa.torrentbot.movie.MovieMediaType;
import ru.xataaa.torrentbot.movie.MovieMetadata;
import ru.xataaa.torrentbot.movie.MovieMetadataService;

class TelegramInlineQueryRouterTest {

    private final MovieMetadataService movieMetadataService = org.mockito.Mockito.mock(MovieMetadataService.class);
    private final TelegramInlineResultFactory telegramInlineResultFactory = org.mockito.Mockito.mock(TelegramInlineResultFactory.class);
    private final TelegramMessageService telegramMessageService = org.mockito.Mockito.mock(TelegramMessageService.class);
    private final SearchProperties searchProperties = new SearchProperties(30, 0, "ru-RU", 30, "", "");
    private final Executor directExecutor = Runnable::run;
    private final TelegramInlineQueryRouter router = new TelegramInlineQueryRouter(
            movieMetadataService,
            telegramInlineResultFactory,
            telegramMessageService,
            searchProperties,
            new SimpleMeterRegistry(),
            directExecutor
    );

    @Test
    void shouldAnswerInlineQueryFastAndWarmTmdbCacheWhenCacheMiss() {
        when(movieMetadataService.findCached("Аватар")).thenReturn(Optional.empty());

        router.route("inline-1", 42L, " Аватар ");

        verify(telegramMessageService).answerInlineQuery("inline-1", "[]", 1);
        verify(movieMetadataService).warmCache("Аватар");
        verify(movieMetadataService, never()).search("Аватар");
        verifyNoInteractions(telegramInlineResultFactory);
    }

    @Test
    void shouldAnswerInlineQueryFromCacheWithoutWarmingTmdb() {
        MovieMetadata movie = new MovieMetadata(
                "selection-1",
                "19995",
                MovieMediaType.MOVIE,
                "Аватар",
                "Avatar",
                2009,
                7.6,
                "",
                ""
        );
        List<MovieMetadata> cachedMovies = List.of(movie);
        when(movieMetadataService.findCached("Аватар")).thenReturn(Optional.of(cachedMovies));
        when(telegramInlineResultFactory.movieResults(cachedMovies)).thenReturn("[{\"id\":\"selection-1\"}]");

        router.route("inline-2", 42L, "Аватар");

        verify(telegramInlineResultFactory).movieResults(cachedMovies);
        verify(telegramMessageService).answerInlineQuery("inline-2", "[{\"id\":\"selection-1\"}]", 30);
        verify(movieMetadataService, never()).warmCache("Аватар");
        verify(movieMetadataService, never()).search("Аватар");
    }
}
