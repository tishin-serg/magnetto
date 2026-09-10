package ru.xataaa.torrentbot.speed;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.job.DownloadJob;
import ru.xataaa.torrentbot.job.DownloadJobRepository;
import ru.xataaa.torrentbot.job.DownloadJobService;
import ru.xataaa.torrentbot.job.DownloadJobStatus;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;
import ru.xataaa.torrentbot.common.TimeProvider;

@Service
@RequiredArgsConstructor
public class DownloadSpeedDecisionService {
    private final DownloadSpeedMonitorRepository monitorRepository;
    private final DownloadAlternativeRepository alternativeRepository;
    private final DownloadJobRepository jobRepository;
    private final DownloadJobService jobService;
    private final QbittorrentTorrentService torrents;
    private final TelegramMessageService messages;
    private final TimeProvider timeProvider;

    public void thresholdReached(DownloadJob job, long speedBytesPerSecond) {
        if (!monitorRepository.markAlertSent(job.getId())) return;
        List<DownloadAlternative> alternatives = alternativeRepository.findByJobId(job.getId());
        if (job.isAutoReplaceSlowDownload() && !alternatives.isEmpty()) {
            try {
                if (replace(job, alternatives.getFirst())) {
                    messages.sendText(job.getChatId(), alertText(job, speedBytesPerSecond)
                            + "\n\nАвтозамена включена: текущая раздача поставлена на паузу, запускаю «"
                            + title(alternatives.getFirst()) + "».");
                    return;
                }
            } catch (RuntimeException exception) {
                // Alert is already claimed; show the manual decision below instead of losing it.
            }
        }
        messages.sendTextWithInlineKeyboard(job.getChatId(), alertText(job, speedBytesPerSecond),
                decisionKeyboard(job.getId(), !alternatives.isEmpty()));
    }

    public boolean replace(DownloadJob job, DownloadAlternative selected) {
        if (job.getStatus() != DownloadJobStatus.DOWNLOADING || job.getTorrentHash() == null) return false;
        UUID replacementId = UUID.randomUUID();
        torrents.pauseTorrent(job.getDownloadTarget(), job.getTorrentHash());
        if (!monitorRepository.claimReplacement(job.getId(), replacementId, timeProvider.now())) return false;
        jobRepository.pauseWithResumeStatus(job.getId(), DownloadJobStatus.DOWNLOADING);
        List<DownloadAlternative> remaining = alternativeRepository.findByJobId(job.getId()).stream()
                .filter(item -> item.position() != selected.position()).toList();
        jobService.startReplacement(replacementId, job, selected, remaining);
        return true;
    }

    public String alertText(DownloadJob job, long speedBytesPerSecond) {
        return "Скорость раздачи ниже установленной: " + speed(speedBytesPerSecond)
                + " при минимуме " + speed(job.getMinDownloadSpeedBytesPerSecond()) + ".";
    }

    public String decisionKeyboard(UUID jobId, boolean hasAlternatives) {
        if (!hasAlternatives) {
            return "{\"inline_keyboard\":[[{\"text\":\"🔎 Новый поиск\",\"callback_data\":\"menu:search\"}],"
                    + "[{\"text\":\"▶️ Оставить текущую\",\"callback_data\":\"speed:keep:" + jobId + "\"}]]}";
        }
        return "{\"inline_keyboard\":[[{\"text\":\"🔄 Выбрать другую раздачу\",\"callback_data\":\"speed:list:" + jobId + "\"}],"
                + "[{\"text\":\"▶️ Оставить текущую\",\"callback_data\":\"speed:keep:" + jobId + "\"}]]}";
    }

    private String speed(long bytesPerSecond) {
        if (bytesPerSecond < 1_000_000L) return Math.round(bytesPerSecond / 1_000.0) + " КБ/с";
        double mb = bytesPerSecond / 1_000_000.0;
        return (mb == Math.rint(mb) ? Long.toString(Math.round(mb)) : String.format(java.util.Locale.ROOT, "%.1f", mb)) + " МБ/с";
    }

    private String title(DownloadAlternative alternative) {
        return alternative.title() == null || alternative.title().isBlank() ? "другая раздача" : alternative.title();
    }
}
