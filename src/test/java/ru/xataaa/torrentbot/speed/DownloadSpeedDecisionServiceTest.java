package ru.xataaa.torrentbot.speed;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.job.*;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;

class DownloadSpeedDecisionServiceTest {
    final DownloadSpeedMonitorRepository monitors = mock(DownloadSpeedMonitorRepository.class);
    final DownloadAlternativeRepository alternatives = mock(DownloadAlternativeRepository.class);
    final DownloadJobRepository jobs = mock(DownloadJobRepository.class);
    final DownloadJobService jobService = mock(DownloadJobService.class);
    final QbittorrentTorrentService torrents = mock(QbittorrentTorrentService.class);
    final TelegramMessageService messages = mock(TelegramMessageService.class);
    final TimeProvider time = mock(TimeProvider.class);
    final DownloadSpeedDecisionService service = new DownloadSpeedDecisionService(monitors, alternatives, jobs, jobService, torrents, messages, time);

    @Test void alertIsSentExactlyOnce() {
        DownloadJob job = job(false);
        when(monitors.markAlertSent(job.getId())).thenReturn(true, false);
        when(alternatives.findByJobId(job.getId())).thenReturn(List.of());

        service.thresholdReached(job, 420_000);
        service.thresholdReached(job, 300_000);

        verify(messages, times(1)).sendTextWithInlineKeyboard(eq(42L), contains("420 КБ/с"), contains("menu:search"));
    }

    @Test void enabledAutoModeUsesFirstAlternative() {
        DownloadJob job = job(true);
        DownloadAlternative first = new DownloadAlternative(0, "magnet:?xt=urn:btih:first", "First", 10);
        DownloadSpeedDecisionService spy = spy(service);
        when(monitors.markAlertSent(job.getId())).thenReturn(true);
        when(alternatives.findByJobId(job.getId())).thenReturn(List.of(first));
        doReturn(true).when(spy).replace(job, first);

        spy.thresholdReached(job, 420_000);

        verify(spy).replace(job, first);
        verify(messages).sendText(eq(42L), contains("Автозамена включена"));
        verify(messages, never()).sendTextWithInlineKeyboard(anyLong(), anyString(), anyString());
    }

    private DownloadJob job(boolean auto) {
        return DownloadJob.builder().id(UUID.randomUUID()).chatId(42L).status(DownloadJobStatus.DOWNLOADING)
                .minDownloadSpeedBytesPerSecond(2_000_000).autoReplaceSlowDownload(auto).build();
    }
}
