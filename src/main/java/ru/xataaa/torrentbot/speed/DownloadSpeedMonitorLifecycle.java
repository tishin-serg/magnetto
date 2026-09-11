package ru.xataaa.torrentbot.speed;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.TimeProvider;

@Service
@RequiredArgsConstructor
public class DownloadSpeedMonitorLifecycle {
    private final DownloadSpeedMonitorRepository repository;
    private final TimeProvider timeProvider;

    public void start(UUID jobId, long thresholdBytesPerSecond) {
        if (thresholdBytesPerSecond > 0) repository.start(jobId, timeProvider.now());
    }

    public void stop(UUID jobId) {
        repository.stop(jobId, timeProvider.now());
    }
}
