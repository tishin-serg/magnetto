package ru.xataaa.torrentbot.torrentsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.config.JacredProperties;

class TorrentSearchServiceCacheTest {

    @Test
    void shouldReuseCachedJacredSearchResults() {
        JacredClient jacredClient = mock(JacredClient.class);
        TorrentSearchRequest request = TorrentSearchRequest.fromUserText("Matrix 1999");
        when(jacredClient.search(request, false)).thenReturn(List.of(result("Matrix 1999", 10)));
        TorrentSearchService service = new TorrentSearchService(
                jacredClient,
                new JacredProperties("http://jacred", "key", 1000, 8000, 5),
                new TorrentSearchCache(),
                new FileSizeFormatter(),
                new SimpleMeterRegistry()
        );

        List<TorrentSearchResult> first = service.search(request);
        List<TorrentSearchResult> second = service.search(request);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(first.getFirst().selectionId()).isNotEqualTo(second.getFirst().selectionId());
        verify(jacredClient, times(1)).search(request, false);
    }

    private JacredSearchResult result(String title, int seeders) {
        JacredSearchResult result = new JacredSearchResult();
        result.setTitle(title);
        result.setMagnetUri("magnet:?xt=urn:btih:0123456789012345678901234567890123456789");
        result.setTracker("tracker");
        result.setSize(1024L);
        result.setSeeders(seeders);
        result.setPeers(1);
        return result;
    }
}
