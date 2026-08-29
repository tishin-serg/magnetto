package ru.xataaa.torrentbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DownloadTargetSelectionCacheTest {

    @Test
    void shouldReturnPendingDownloadForSameChat() {
        DownloadTargetSelectionCache cache = new DownloadTargetSelectionCache();

        String selectionId = cache.put(42L, "magnet:?xt=urn:btih:abcdef", 123L, "Movie");

        assertThat(cache.find(selectionId, 42L)).isPresent();
        assertThat(cache.find(selectionId, 42L).get().expectedSizeBytes()).isEqualTo(123L);
    }

    @Test
    void shouldNotReturnPendingDownloadForDifferentChat() {
        DownloadTargetSelectionCache cache = new DownloadTargetSelectionCache();

        String selectionId = cache.put(42L, "magnet:?xt=urn:btih:abcdef", 123L, "Movie");

        assertThat(cache.find(selectionId, 43L)).isEmpty();
    }
}
