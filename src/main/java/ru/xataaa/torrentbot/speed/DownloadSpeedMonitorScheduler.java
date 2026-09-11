package ru.xataaa.torrentbot.speed;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.job.DownloadJob;
import ru.xataaa.torrentbot.job.DownloadJobRepository;
import ru.xataaa.torrentbot.job.DownloadJobStatus;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;

@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadSpeedMonitorScheduler {
    private final DownloadSpeedMonitorRepository monitorRepository;
    private final DownloadJobRepository jobRepository;
    private final QbittorrentTorrentService torrents;
    private final DownloadSpeedDecisionService decisions;
    private final TimeProvider timeProvider;

    @Scheduled(fixedDelay = 60_000)
    public void checkDueMonitors() {
        LocalDateTime now = timeProvider.now();
        for (DownloadSpeedMonitor monitor : monitorRepository.findDue(now, 50)) {
            check(monitor, now);
        }
    }

    private void check(DownloadSpeedMonitor monitor, LocalDateTime now) {
        if (!now.isBefore(monitor.endsAt())) {
            monitorRepository.stop(monitor.jobId(), now);
            return;
        }
        DownloadJob job = jobRepository.findById(monitor.jobId()).orElse(null);
        boolean downloading = job != null && (job.getStatus() == DownloadJobStatus.DOWNLOADING
                || (job.getStatus() == DownloadJobStatus.RETRY_WAITING && job.getResumeStatus() == DownloadJobStatus.DOWNLOADING));
        if (!downloading || job.getTorrentHash() == null) {
            monitorRepository.stop(monitor.jobId(), now);
            return;
        }
        try {
            var info = torrents.getTorrentInfoByHash(job.getDownloadTarget(), job.getTorrentHash()).orElse(null);
            if (info == null) {
                monitorRepository.recordMeasurementError(job.getId(), now);
                return;
            }
            long speed = info.getDownloadSpeed();
            monitorRepository.recordMeasurement(job.getId(), speed,
                    speed < job.getMinDownloadSpeedBytesPerSecond(), now);
            DownloadSpeedMonitor updated = monitorRepository.find(job.getId()).orElse(monitor);
            if (updated.consecutiveLowChecks() >= 3 && !updated.alertSent()) decisions.thresholdReached(job, speed);
        } catch (RuntimeException exception) {
            monitorRepository.recordMeasurementError(job.getId(), now);
            log.warn("Download speed measurement failed: jobId={}, error={}", job.getId(), exception.getMessage());
        }
    }
}
