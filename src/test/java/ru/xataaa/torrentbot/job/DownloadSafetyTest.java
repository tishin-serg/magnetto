package ru.xataaa.torrentbot.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.xataaa.torrentbot.file.*;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;
import ru.xataaa.torrentbot.qbittorrent.dto.QbittorrentTorrentInfo;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;

@ExtendWith(MockitoExtension.class)
class DownloadSafetyTest {
    @Mock DownloadJobRepository jobs;
    @Mock DownloadFileRepository files;
    @Mock QbittorrentTorrentService torrents;
    @Mock FileDiscoveryService discovery;
    @Mock DownloadTelemetry telemetry;
    @Mock ru.xataaa.torrentbot.telegram.TelegramMessageService messages;
    @Mock ru.xataaa.torrentbot.common.FileSizeFormatter sizes;
    @InjectMocks DownloadOrchestrator orchestrator;

    @Test void emptySelectionMustPauseAndNeverStartDownloading() {
        var id = UUID.randomUUID();
        var job = DownloadJob.builder().id(id).torrentHash("abc").downloadTarget(DownloadTarget.HOME_PC).build();
        var info = new QbittorrentTorrentInfo();
        info.setHash("abc"); info.setName("No videos"); info.setState("pausedDL");
        when(torrents.getTorrentInfoByHash(DownloadTarget.HOME_PC, "abc")).thenReturn(Optional.of(info));
        when(files.findByJobIdAndStatuses(id, List.of(DownloadFileStatus.READY_TO_UPLOAD))).thenReturn(List.of());
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(orchestrator, "handleWaitingMetadata", job))
                .isInstanceOf(NonRetryableOperationException.class).hasMessageContaining("нет поддерживаемых");
        verify(torrents).pauseTorrent(DownloadTarget.HOME_PC, "abc");
        verify(torrents, never()).resumeTorrent(any(), anyString());
    }

    @Test void acceptsBluRayVideoButNotDiscMetadata() {
        var filter = new FileFilterService();
        assertThat(filter.classify("BDMV/STREAM/00002.M2TS", 23000000000L)).isEqualTo(DownloadFileStatus.READY_TO_UPLOAD);
        assertThat(filter.classify("index.bdmv", 300)).isEqualTo(DownloadFileStatus.SKIPPED_UNSUPPORTED);
    }

    @Test void updatesAtOnePercentAndDoesNotRepeatSamePercent() {
        var id = UUID.randomUUID();
        var job = DownloadJob.builder().id(id).chatId(1L).statusMessageId(2L)
                .downloadTarget(DownloadTarget.HOME_PC).lastReportedProgressPercent(0).build();
        var info = new QbittorrentTorrentInfo();
        info.setName("Movie"); info.setProgress(0.01);
        ReflectionTestUtils.invokeMethod(orchestrator, "maybeSendProgress", job, info, 1);
        verify(messages).editText(eq(1L), eq(2L), contains("1%"), isNull());
        verify(jobs).updateLastReportedProgress(id, 1);
        job.setLastReportedProgressPercent(1);
        ReflectionTestUtils.invokeMethod(orchestrator, "maybeSendProgress", job, info, 1);
        verifyNoMoreInteractions(messages);
    }
}
