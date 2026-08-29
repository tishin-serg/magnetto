package ru.xataaa.torrentbot.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.config.TmdbProperties;

class MovieMetadataServiceTest {

    @Test
    void shouldCoalesceParallelTmdbSearchesForSameQuery() throws Exception {
        TmdbClient tmdbClient = mock(TmdbClient.class);
        CountDownLatch searchStarted = new CountDownLatch(1);
        CountDownLatch releaseSearch = new CountDownLatch(1);
        when(tmdbClient.searchMulti("Black Mirror")).thenAnswer(invocation -> {
            searchStarted.countDown();
            releaseSearch.await();
            return List.of(tmdbResult());
        });
        when(tmdbClient.posterUrl(anyString())).thenReturn("");
        MovieMetadataService service = new MovieMetadataService(
                tmdbClient,
                new TmdbProperties("key", "http://tmdb", "http://img", "ru-RU", 1000, 1000, 60, 1440, 10),
                new SimpleMeterRegistry()
        );

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<List<MovieMetadata>> first = executorService.submit(() -> service.search("Black Mirror"));
            searchStarted.await();
            Future<List<MovieMetadata>> second = executorService.submit(() -> service.search("Black Mirror"));
            releaseSearch.countDown();

            assertThat(first.get()).hasSize(1);
            assertThat(second.get()).hasSize(1);
            verify(tmdbClient, times(1)).searchMulti("Black Mirror");
        } finally {
            executorService.shutdownNow();
        }
    }

    private TmdbSearchResponse.TmdbSearchResult tmdbResult() {
        TmdbSearchResponse.TmdbSearchResult result = new TmdbSearchResponse.TmdbSearchResult();
        result.setId(42009L);
        result.setMediaType("tv");
        result.setName("Black Mirror");
        result.setOriginalName("Black Mirror");
        result.setFirstAirDate("2011-12-04");
        result.setVoteAverage(8.3);
        return result;
    }
}
