package ru.xataaa.torrentbot.speed;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.job.*;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;
import ru.xataaa.torrentbot.qbittorrent.dto.QbittorrentTorrentInfo;

class DownloadSpeedMonitorSchedulerTest {
    final DownloadSpeedMonitorRepository monitors = mock(DownloadSpeedMonitorRepository.class);
    final DownloadJobRepository jobs = mock(DownloadJobRepository.class);
    final QbittorrentTorrentService torrents = mock(QbittorrentTorrentService.class);
    final DownloadSpeedDecisionService decisions = mock(DownloadSpeedDecisionService.class);
    final TimeProvider time = mock(TimeProvider.class);
    final DownloadSpeedMonitorScheduler scheduler = new DownloadSpeedMonitorScheduler(monitors, jobs, torrents, decisions, time);
    final UUID id = UUID.randomUUID();
    final LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);

    @BeforeEach void setup() { when(time.now()).thenReturn(now); }

    @Test void thirdConsecutiveLowMeasurementTriggersOneDecision() {
        DownloadSpeedMonitor due = monitor(2, false, now.plusMinutes(10));
        DownloadJob job = job(DownloadJobStatus.DOWNLOADING);
        QbittorrentTorrentInfo info = new QbittorrentTorrentInfo(); info.setDownloadSpeed(420_000);
        when(monitors.findDue(now, 50)).thenReturn(List.of(due));
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        when(torrents.getTorrentInfoByHash(DownloadTarget.VPS, "hash")).thenReturn(Optional.of(info));
        when(monitors.find(id)).thenReturn(Optional.of(monitor(3, false, now.plusMinutes(10))));

        scheduler.checkDueMonitors();

        verify(monitors).recordMeasurement(id, 420_000, true, now);
        verify(decisions).thresholdReached(job, 420_000);
    }

    @Test void highMeasurementResetsSequenceAndSentAlertDoesNotRepeat() {
        QbittorrentTorrentInfo info = new QbittorrentTorrentInfo(); info.setDownloadSpeed(3_000_000);
        when(monitors.findDue(now, 50)).thenReturn(List.of(monitor(2, false, now.plusMinutes(10))));
        when(jobs.findById(id)).thenReturn(Optional.of(job(DownloadJobStatus.DOWNLOADING)));
        when(torrents.getTorrentInfoByHash(any(), anyString())).thenReturn(Optional.of(info));
        when(monitors.find(id)).thenReturn(Optional.of(monitor(0, true, now.plusMinutes(10))));

        scheduler.checkDueMonitors();

        verify(monitors).recordMeasurement(id, 3_000_000, false, now);
        verifyNoInteractions(decisions);
    }

    @Test void qbittorrentFailureIsMeasurementErrorNotZeroSpeed() {
        when(monitors.findDue(now, 50)).thenReturn(List.of(monitor(2, false, now.plusMinutes(10))));
        when(jobs.findById(id)).thenReturn(Optional.of(job(DownloadJobStatus.DOWNLOADING)));
        when(torrents.getTorrentInfoByHash(any(), anyString())).thenThrow(new IllegalStateException("offline"));

        scheduler.checkDueMonitors();

        verify(monitors).recordMeasurementError(id, now);
        verify(monitors, never()).recordMeasurement(any(), anyLong(), anyBoolean(), any());
        verifyNoInteractions(decisions);
    }

    @Test void recoverableQbittorrentRetryKeepsThePersistentMonitorAlive() {
        DownloadJob retrying = job(DownloadJobStatus.RETRY_WAITING);
        retrying.setResumeStatus(DownloadJobStatus.DOWNLOADING);
        QbittorrentTorrentInfo info = new QbittorrentTorrentInfo(); info.setDownloadSpeed(3_000_000);
        when(monitors.findDue(now, 50)).thenReturn(List.of(monitor(0, false, now.plusMinutes(10))));
        when(jobs.findById(id)).thenReturn(Optional.of(retrying));
        when(torrents.getTorrentInfoByHash(any(), anyString())).thenReturn(Optional.of(info));
        when(monitors.find(id)).thenReturn(Optional.of(monitor(0, false, now.plusMinutes(10))));

        scheduler.checkDueMonitors();

        verify(monitors, never()).stop(id, now);
        verify(monitors).recordMeasurement(id, 3_000_000, false, now);
    }

    @Test void expiryAndCompletionAndPauseStopMonitoring() {
        when(monitors.findDue(now, 50)).thenReturn(List.of(monitor(0, false, now)));
        scheduler.checkDueMonitors();
        verify(monitors).stop(id, now);

        reset(monitors);
        when(monitors.findDue(now, 50)).thenReturn(List.of(monitor(0, false, now.plusMinutes(10))));
        when(jobs.findById(id)).thenReturn(Optional.of(job(DownloadJobStatus.DOWNLOAD_COMPLETED)));
        scheduler.checkDueMonitors();
        verify(monitors).stop(id, now);

        reset(monitors);
        when(monitors.findDue(now, 50)).thenReturn(List.of(monitor(0, false, now.plusMinutes(10))));
        when(jobs.findById(id)).thenReturn(Optional.of(job(DownloadJobStatus.PAUSED_BY_USER)));
        scheduler.checkDueMonitors();
        verify(monitors).stop(id, now);
    }

    private DownloadSpeedMonitor monitor(int low, boolean sent, LocalDateTime ends) {
        return new DownloadSpeedMonitor(id, now.minusMinutes(1), ends, now, 420_000L, low, sent, false, null, null);
    }

    private DownloadJob job(DownloadJobStatus status) {
        return DownloadJob.builder().id(id).chatId(42L).status(status).torrentHash("hash")
                .downloadTarget(DownloadTarget.VPS).minDownloadSpeedBytesPerSecond(2_000_000).build();
    }
}
