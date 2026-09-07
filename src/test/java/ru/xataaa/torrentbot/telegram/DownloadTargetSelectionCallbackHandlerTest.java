package ru.xataaa.torrentbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.common.DiskSpaceService;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.job.DownloadJobService;
import ru.xataaa.torrentbot.job.DownloadTarget;
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
                s3MediaLibraryService,
                mock(DiskSpaceService.class),
                mock(FileSizeFormatter.class)
        );

        handler.handle("callback-1", 42L, 100L, "target:select:" + selectionId + ":S3");

        verify(telegramMessageService).answerCallbackQuery("callback-1", "S3 не настроен");
        verify(telegramMessageService).editText(eq(42L), eq(100L), contains("Облако S3 недоступно"), contains(":HOME_PC"));
        verify(downloadJobService, never()).startDownload(any(), any(), anyLong(), any(), any());
    }

    @Test
    void shouldRejectDiskTargetBeforeCreatingJobWhenFileDoesNotFit() {
        DownloadTargetSelectionCache cache = new DownloadTargetSelectionCache();
        String selectionId = cache.put(42L, "magnet:?xt=urn:btih:0123456789012345678901234567890123456789", 200L, "Movie");
        DownloadJobService downloadJobService = mock(DownloadJobService.class);
        TelegramMessageService telegramMessageService = mock(TelegramMessageService.class);
        DiskSpaceService diskSpaceService = mock(DiskSpaceService.class);
        FileSizeFormatter fileSizeFormatter = mock(FileSizeFormatter.class);
        when(diskSpaceService.downloadStorageInfo(DownloadTarget.HOME_PC))
                .thenReturn(new DiskSpaceService.DiskSpaceInfo(-1L, 100L));
        when(fileSizeFormatter.format(200L)).thenReturn("200 B");
        when(fileSizeFormatter.format(100L)).thenReturn("100 B");
        DownloadTargetSelectionCallbackHandler handler = new DownloadTargetSelectionCallbackHandler(
                cache,
                downloadJobService,
                telegramMessageService,
                mock(S3MediaLibraryService.class),
                diskSpaceService,
                fileSizeFormatter
        );

        handler.handle("callback-1", 42L, 100L, "target:select:" + selectionId + ":HOME_PC");

        verify(telegramMessageService).answerCallbackQuery("callback-1", "Недостаточно свободного места");
        verify(telegramMessageService).editText(eq(42L), eq(100L), contains("100 B"), contains(":VPS"));
        verify(downloadJobService, never()).startDownload(any(), any(), anyLong(), any(), any());
        assertThat(cache.find(selectionId, 42L)).isPresent();
    }

    @Test
    void shouldNotCheckDiskSpaceForS3() {
        DownloadTargetSelectionCache cache = new DownloadTargetSelectionCache();
        String selectionId = cache.put(42L, "magnet:?xt=urn:btih:0123456789012345678901234567890123456789", 200L, "Movie");
        DownloadJobService downloadJobService = mock(DownloadJobService.class);
        S3MediaLibraryService s3MediaLibraryService = mock(S3MediaLibraryService.class);
        DiskSpaceService diskSpaceService = mock(DiskSpaceService.class);
        when(s3MediaLibraryService.isEnabled()).thenReturn(true);
        when(s3MediaLibraryService.isConfigured()).thenReturn(true);
        DownloadTargetSelectionCallbackHandler handler = new DownloadTargetSelectionCallbackHandler(
                cache,
                downloadJobService,
                mock(TelegramMessageService.class),
                s3MediaLibraryService,
                diskSpaceService,
                mock(FileSizeFormatter.class)
        );

        handler.handle("callback-1", 42L, 100L, "target:select:" + selectionId + ":S3");

        verifyNoInteractions(diskSpaceService);
        verify(downloadJobService).startDownload(42L,
                "magnet:?xt=urn:btih:0123456789012345678901234567890123456789",
                200L, DownloadTarget.S3, "Movie");
    }
}
