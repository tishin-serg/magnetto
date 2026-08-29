package ru.xataaa.torrentbot.downloadlink;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class DownloadLinkCleanupSchedulerTest {

    @Test
    void shouldExpireAndDeleteOldLinks() {
        DownloadLinkService downloadLinkService = mock(DownloadLinkService.class);
        HomeDownloadLinkService homeDownloadLinkService = mock(HomeDownloadLinkService.class);
        when(downloadLinkService.expireOldLinks()).thenReturn(2);
        when(downloadLinkService.deleteExpiredFiles()).thenReturn(1);
        when(homeDownloadLinkService.expireOldLinks()).thenReturn(1);
        DownloadLinkCleanupScheduler scheduler = new DownloadLinkCleanupScheduler(downloadLinkService, homeDownloadLinkService);

        scheduler.cleanupExpiredLinks();

        verify(downloadLinkService).expireOldLinks();
        verify(downloadLinkService).deleteExpiredFiles();
        verify(homeDownloadLinkService).expireOldLinks();
    }
}
