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
        TaskOverviewService service = new TaskOverviewService(repository, mock(TimeProvider.class));

        assertThat(service.keyboard()).contains(
                "task:pause:" + downloadingId,
                "task:resume:" + pausedId,
                "task:list",
                "menu:home"
        );
        assertThat(service.text()).contains("Movie", "Series", "домашний ПК", "S3");
    }
}
