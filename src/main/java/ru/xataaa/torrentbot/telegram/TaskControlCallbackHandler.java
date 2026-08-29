package ru.xataaa.torrentbot.telegram;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.job.DownloadJob;
import ru.xataaa.torrentbot.job.DownloadJobRepository;
import ru.xataaa.torrentbot.job.DownloadJobStatus;
import ru.xataaa.torrentbot.job.DownloadOrchestrator;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;

@Component
@RequiredArgsConstructor
public class TaskControlCallbackHandler implements TelegramCallbackHandler {

    private static final String LIST_CALLBACK = "task:list";
    private static final String PAUSE_PREFIX = "task:pause:";
    private static final String RESUME_PREFIX = "task:resume:";

    private final DownloadJobRepository downloadJobRepository;
    private final QbittorrentTorrentService qbittorrentTorrentService;
    private final DownloadOrchestrator downloadOrchestrator;
    private final TelegramMessageService telegramMessageService;
    private final TaskOverviewService taskOverviewService;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith("task:");
    }

    @Override
    public void handle(String callbackQueryId, Long chatId, Long messageId, String data) {
        if (LIST_CALLBACK.equals(data)) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Обновил задачи");
            telegramMessageService.editText(chatId, messageId, taskOverviewService.text(), taskOverviewService.keyboard());
            return;
        }
        if (data.startsWith(PAUSE_PREFIX)) {
            handlePause(callbackQueryId, chatId, messageId, data.substring(PAUSE_PREFIX.length()));
            return;
        }
        if (data.startsWith(RESUME_PREFIX)) {
            handleResume(callbackQueryId, chatId, messageId, data.substring(RESUME_PREFIX.length()));
        }
    }

    private void handlePause(String callbackQueryId, Long chatId, Long messageId, String jobIdValue) {
        DownloadJob downloadJob = findJob(jobIdValue);
        if (downloadJob == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Задача не найдена");
            return;
        }
        if (!canPause(downloadJob)) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Эту задачу сейчас нельзя поставить на паузу");
            return;
        }
        if (downloadJob.getTorrentHash() != null && !downloadJob.getTorrentHash().isBlank()) {
            qbittorrentTorrentService.pauseTorrent(downloadTarget(downloadJob), downloadJob.getTorrentHash());
        }
        downloadJobRepository.pauseWithResumeStatus(downloadJob.getId(), downloadJob.getStatus());
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Задача поставлена на паузу");
        telegramMessageService.editText(chatId, messageId, taskOverviewService.text(), taskOverviewService.keyboard());
    }

    private void handleResume(String callbackQueryId, Long chatId, Long messageId, String jobIdValue) {
        DownloadJob downloadJob = findJob(jobIdValue);
        if (downloadJob == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Задача не найдена");
            return;
        }
        DownloadJobStatus resumeStatus = downloadJob.getResumeStatus() == null
                ? fallbackResumeStatus(downloadJob)
                : downloadJob.getResumeStatus();
        if (downloadJob.getTorrentHash() != null && !downloadJob.getTorrentHash().isBlank()) {
            qbittorrentTorrentService.resumeTorrent(downloadTarget(downloadJob), downloadJob.getTorrentHash());
        }
        downloadJobRepository.updateStatus(downloadJob.getId(), resumeStatus);
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Задача продолжена");
        telegramMessageService.editText(chatId, messageId, taskOverviewService.text(), taskOverviewService.keyboard());
        downloadOrchestrator.processJob(downloadJob.getId());
    }

    private DownloadJob findJob(String jobIdValue) {
        UUID jobId = UUID.fromString(jobIdValue);
        return downloadJobRepository.findById(jobId).orElse(null);
    }

    private boolean canPause(DownloadJob downloadJob) {
        if (downloadJob.getTorrentHash() == null || downloadJob.getTorrentHash().isBlank()) {
            return false;
        }
        return downloadJob.getStatus() == DownloadJobStatus.WAITING_METADATA
                || downloadJob.getStatus() == DownloadJobStatus.DOWNLOADING
                || downloadJob.getStatus() == DownloadJobStatus.RETRY_WAITING;
    }

    private DownloadJobStatus fallbackResumeStatus(DownloadJob downloadJob) {
        if (downloadJob.getTorrentHash() == null || downloadJob.getTorrentHash().isBlank()) {
            return DownloadJobStatus.CREATED;
        }
        return DownloadJobStatus.DOWNLOADING;
    }

    private DownloadTarget downloadTarget(DownloadJob downloadJob) {
        return downloadJob.getDownloadTarget() == null ? DownloadTarget.VPS : downloadJob.getDownloadTarget();
    }
}
