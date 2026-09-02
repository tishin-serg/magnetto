package ru.xataaa.torrentbot.telegram;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.job.DownloadJobService;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;

class DownloadTargetSelectionCallbackHandlerTest {

    @Test
    void shouldRejectS3SelectionWhenS3IsDisabled() {
        DownloadTargetSelectionCache cache = new DownloadTargetSelectionCache();
        String selectionId = cache.put(42L, "magnet:?xt=urn:btih:0123456789012345678901234567890123456789", 0L, "Movie");
        DownloadJobService downloadJobService = mock(DownloadJobService.class);
        TelegramMessageService telegramMessageService = mock(TelegramMessageService.class);
        S3MediaLibraryService s3MediaLibraryService = mock(S3MediaLibraryService.class);
        when(s3MediaLibraryService.isEnabled()).thenReturn(false);
        when(s3MediaLibraryService.isConfigured()).thenReturn(true);
        DownloadTargetSelectionCallbackHandler handler = new DownloadTargetSelectionCallbackHandler(
                cache,
                downloadJobService,
                telegramMessageService,
                s3MediaLibraryService
        );

        handler.handle("callback-1", 42L, 100L, "target:select:" + selectionId + ":S3");

        verify(telegramMessageService).answerCallbackQuery("callback-1", "S3 не настроен");
        verify(telegramMessageService).editText(eq(42L), eq(100L), contains("S3 сейчас выключен или не настроен"), any());
        verify(downloadJobService, never()).startDownload(any(), any(), anyLong(), any(), any());
    }
}
