package ru.xataaa.torrentbot.downloadlink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadLinkCleanupScheduler {

    private final DownloadLinkService downloadLinkService;
    private final HomeDownloadLinkService homeDownloadLinkService;

    @Scheduled(fixedDelayString = "${downloads.cleanup-interval-ms:1800000}")
    public void cleanupExpiredLinks() {
        int expiredCount = downloadLinkService.expireOldLinks();
        int deletedCount = downloadLinkService.deleteExpiredFiles();
        int expiredHomeLinks = homeDownloadLinkService.expireOldLinks();
        if (expiredCount > 0 || deletedCount > 0 || expiredHomeLinks > 0) {
            log.info("Download link cleanup completed: expiredLinks={}, deletedFiles={}, expiredHomeLinks={}",
                    expiredCount, deletedCount, expiredHomeLinks);
        }
    }
}
