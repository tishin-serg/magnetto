package ru.xataaa.torrentbot.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;

class CleanupServiceTest {

    private final DownloadFileRepository downloadFileRepository = mock(DownloadFileRepository.class);
    private final QbittorrentTorrentService qbittorrentTorrentService = mock(QbittorrentTorrentService.class);
    private final CleanupService cleanupService = new CleanupService(downloadFileRepository, qbittorrentTorrentService);

    @Test
    void shouldForbidCleanupWhenFilesHaveUnknownUploadResult() {
        UUID jobId = UUID.randomUUID();
        when(downloadFileRepository.findByJobId(jobId)).thenReturn(List.of(file(jobId, DownloadFileStatus.UNKNOWN_UPLOAD_RESULT)));

        assertThat(cleanupService.canCleanup(jobId)).isFalse();
    }

    @Test
    void shouldForbidCleanupWhenFilesHaveRetryableUploadFailure() {
        UUID jobId = UUID.randomUUID();
        when(downloadFileRepository.findByJobId(jobId)).thenReturn(List.of(file(jobId, DownloadFileStatus.UPLOAD_FAILED_RETRYABLE)));

        assertThat(cleanupService.canCleanup(jobId)).isFalse();
    }

    @Test
    void shouldAllowCleanupWhenRequiredFilesUploadedAndOtherFilesSkipped() {
        UUID jobId = UUID.randomUUID();
        when(downloadFileRepository.findByJobId(jobId)).thenReturn(List.of(
                file(jobId, DownloadFileStatus.UPLOADED),
                file(jobId, DownloadFileStatus.DOWNLOAD_LINK_CREATED),
                file(jobId, DownloadFileStatus.SKIPPED_TOO_LARGE),
                file(jobId, DownloadFileStatus.SKIPPED_UNSUPPORTED)
        ));

        assertThat(cleanupService.canCleanup(jobId)).isTrue();
    }

    private DownloadFile file(UUID jobId, DownloadFileStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return DownloadFile.builder()
                .id(UUID.randomUUID())
                .jobId(jobId)
                .fileName("movie.mkv")
                .relativePath("movie.mkv")
                .sizeBytes(100)
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
