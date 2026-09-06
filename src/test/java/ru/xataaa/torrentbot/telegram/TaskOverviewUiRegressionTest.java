package ru.xataaa.torrentbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.job.DownloadJob;
import ru.xataaa.torrentbot.job.DownloadJobRepository;
import ru.xataaa.torrentbot.job.DownloadJobStatus;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.regression.UiRegression;

@UiRegression
class TaskOverviewUiRegressionTest {

    @Test
    void shouldShowLiveSelectedSizeSpeedAndProgress() {
        var repository = mock(DownloadJobRepository.class);
        var id = UUID.randomUUID();
        when(repository.findRecent(10)).thenReturn(List.of(DownloadJob.builder().id(id)
                .torrentName("Movie").status(DownloadJobStatus.DOWNLOADING).build()));
        var telemetry = new ru.xataaa.torrentbot.job.DownloadTelemetry();
        var info = new ru.xataaa.torrentbot.qbittorrent.dto.QbittorrentTorrentInfo();
        info.setProgress(0.42);
        info.setSize(1024L * 1024 * 1024);
        info.setDownloadSpeed(1024 * 1024);
        info.setEta(120);
        info.setState("stalledDL");
        telemetry.record(id, info);
        var service = new TaskOverviewService(repository, mock(TimeProvider.class), telemetry);
        assertThat(service.text()).contains("42%", "Скачано", "Скорость:", "/с", "2 мин", "Ожидание источников");
    }

    @Test
    void shouldExposePauseResumeRefreshAndBackActions() {
        UUID downloadingId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID pausedId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        DownloadJobRepository repository = mock(DownloadJobRepository.class);
        when(repository.findRecent(10)).thenReturn(List.of(
                DownloadJob.builder()
                        .id(downloadingId)
                        .torrentName("Movie")
                        .status(DownloadJobStatus.DOWNLOADING)
                        .downloadTarget(DownloadTarget.HOME_PC)
                        .build(),
                DownloadJob.builder()
                        .id(pausedId)
                        .torrentName("Series")
                        .status(DownloadJobStatus.PAUSED_BY_USER)
                        .downloadTarget(DownloadTarget.S3)
                        .build()
        ));
        TaskOverviewService service = new TaskOverviewService(repository, mock(TimeProvider.class), new ru.xataaa.torrentbot.job.DownloadTelemetry());

        assertThat(service.keyboard()).contains(
                "task:pause:" + downloadingId,
                "task:resume:" + pausedId,
                "task:list",
                "menu:home"
        );
        assertThat(service.text()).contains("Movie", "Series", "домашний ПК", "S3");
    }
}
