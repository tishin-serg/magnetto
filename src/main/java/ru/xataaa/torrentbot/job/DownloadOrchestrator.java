package ru.xataaa.torrentbot.job;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.DiskSpaceService;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.config.AppProperties;
import ru.xataaa.torrentbot.downloadlink.FileDeliveryDecisionService;
import ru.xataaa.torrentbot.downloadlink.FileDeliveryMode;
import ru.xataaa.torrentbot.file.CleanupService;
import ru.xataaa.torrentbot.file.DownloadFile;
import ru.xataaa.torrentbot.file.DownloadFileRepository;
import ru.xataaa.torrentbot.file.DownloadFileStatus;
import ru.xataaa.torrentbot.file.FileDeliveryService;
import ru.xataaa.torrentbot.file.FileDiscoveryService;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;
import ru.xataaa.torrentbot.qbittorrent.dto.QbittorrentTorrentFile;
import ru.xataaa.torrentbot.qbittorrent.dto.QbittorrentTorrentInfo;
import ru.xataaa.torrentbot.media.HomeWebdavMediaLibraryService;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;
import ru.xataaa.torrentbot.retry.RetryableOperationException;
import ru.xataaa.torrentbot.telegram.TelegramKeyboardFactory;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;
import ru.xataaa.torrentbot.telegram.FileSelectionViewFactory;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadOrchestrator {

    private final DownloadJobRepository downloadJobRepository;
    private final DownloadFileRepository downloadFileRepository;
    private final QbittorrentTorrentService qbittorrentTorrentService;
    private final FileDiscoveryService fileDiscoveryService;
    private final FileDeliveryService fileDeliveryService;
    private final CleanupService cleanupService;
    private final TelegramMessageService telegramMessageService;
    private final RetryScheduleService retryScheduleService;
    private final FileSizeFormatter fileSizeFormatter;
    private final FileDeliveryDecisionService fileDeliveryDecisionService;
    private final DiskSpaceService diskSpaceService;
    private final HomeWebdavMediaLibraryService homeWebdavMediaLibraryService;
    private final TelegramKeyboardFactory telegramKeyboardFactory;
    private final FileSelectionViewFactory fileSelectionViewFactory;
    private final TimeProvider timeProvider;
    private final AppProperties appProperties;
    private final ConcurrentMap<UUID, Boolean> runningJobs = new ConcurrentHashMap<>();

    public void processJob(UUID jobId) {
        if (runningJobs.putIfAbsent(jobId, Boolean.TRUE) != null) {
            return;
        }
        try {
            DownloadJob downloadJob = downloadJobRepository.findById(jobId).orElse(null);
            if (downloadJob == null) {
                return;
            }
            processLoadedJob(downloadJob);
        } finally {
            runningJobs.remove(jobId);
        }
    }

    private void processLoadedJob(DownloadJob downloadJob) {
        try {
            failIfJobTooOld(downloadJob);
            DownloadJobStatus effectiveStatus = effectiveStatus(downloadJob);
            switch (effectiveStatus) {
                case QUEUED -> {
                }
                case PAUSED_BY_USER -> {
                }
                case CREATED -> handleCreated(downloadJob);
                case ADDING_TO_QBITTORRENT -> handleAddingToQbittorrent(downloadJob);
                case ADDED_TO_QBITTORRENT, WAITING_METADATA -> handleWaitingMetadata(downloadJob);
                case DOWNLOADING -> handleDownloading(downloadJob);
                case DOWNLOAD_COMPLETED -> changeStatus(downloadJob, DownloadJobStatus.DISCOVERING_FILES);
                case DISCOVERING_FILES -> handleDiscoveringFiles(downloadJob);
                case WAITING_FILE_SELECTION -> {
                }
                case DELIVERY_PENDING -> changeStatus(downloadJob, DownloadJobStatus.UPLOADING_TO_TELEGRAM);
                case UPLOADING_TO_TELEGRAM -> handleUploading(downloadJob);
                case DELIVERY_COMPLETED -> handleDeliveryCompleted(downloadJob);
                case CLEANUP_PENDING -> changeStatus(downloadJob, DownloadJobStatus.CLEANING_UP);
                case CLEANING_UP -> handleCleaningUp(downloadJob);
                case CLEANUP_COMPLETED -> handleFinished(downloadJob);
                case FINISHED, FAILED_FINAL -> {
                }
                default -> scheduleRetry(downloadJob, effectiveStatus, ErrorCode.UNKNOWN_ERROR, "Unsupported job status");
            }
        } catch (NonRetryableOperationException nonRetryableOperationException) {
            failFinally(downloadJob, nonRetryableOperationException.getErrorCode(), nonRetryableOperationException.getMessage());
        } catch (RuntimeException runtimeException) {
            ErrorCode errorCode = runtimeException instanceof RetryableOperationException retryableOperationException
                    ? retryableOperationException.getErrorCode()
                    : ErrorCode.UNKNOWN_ERROR;
            scheduleRetry(downloadJob, effectiveStatus(downloadJob), errorCode, runtimeException.getMessage());
        }
    }

    private DownloadJobStatus effectiveStatus(DownloadJob downloadJob) {
        if (downloadJob.getStatus() == DownloadJobStatus.RETRY_WAITING && downloadJob.getResumeStatus() != null) {
            return downloadJob.getResumeStatus();
        }
        return downloadJob.getStatus();
    }

    private void handleCreated(DownloadJob downloadJob) {
        changeStatus(downloadJob, DownloadJobStatus.ADDING_TO_QBITTORRENT);
        processJob(downloadJob.getId());
    }

    private void handleAddingToQbittorrent(DownloadJob downloadJob) {
        DownloadTarget downloadTarget = downloadTarget(downloadJob);
        log.info("Adding magnet to qBittorrent: jobId={}, chatId={}, downloadTarget={}", downloadJob.getId(), downloadJob.getChatId(), downloadTarget);
        qbittorrentTorrentService.addMagnet(downloadTarget, downloadJob.getId(), downloadJob.getMagnetUrl());
        Optional<QbittorrentTorrentInfo> torrentInfo = qbittorrentTorrentService.getTorrentInfoByJobTag(downloadTarget, downloadJob.getId());
        if (torrentInfo.isEmpty()) {
            throw new RetryableOperationException(ErrorCode.QBITTORRENT_ADD_FAILED, "Torrent was added but hash is not visible yet");
        }
        QbittorrentTorrentInfo info = torrentInfo.get();
        downloadJobRepository.updateTorrentIdentity(downloadJob.getId(), info.getHash(), preferredTorrentName(downloadJob, info.getName()));
        downloadJobRepository.updateTargetStatus(downloadJob.getId(), TargetStatus.READY, null);
        log.info("Magnet added to qBittorrent: jobId={}, torrentHash={}, torrentName={}, downloadTarget={}", downloadJob.getId(), info.getHash(), info.getName(), downloadTarget);
        updateStatusMessage(downloadJob, "Torrent добавлен в qBittorrent (" + targetLabel(downloadTarget) + ").\nЖду metadata...");
        changeStatus(downloadJob, DownloadJobStatus.WAITING_METADATA);
    }

    private void handleWaitingMetadata(DownloadJob downloadJob) {
        Optional<QbittorrentTorrentInfo> torrentInfo = findTorrent(downloadJob);
        if (torrentInfo.isEmpty()) {
            scheduleRetry(downloadJob, DownloadJobStatus.ADDING_TO_QBITTORRENT, ErrorCode.QBITTORRENT_ADD_FAILED, "Torrent not found by saved identity");
            return;
        }
        QbittorrentTorrentInfo info = torrentInfo.get();
        downloadJobRepository.updateTorrentIdentity(downloadJob.getId(), info.getHash(), preferredTorrentName(downloadJob, info.getName()));
        if (!info.hasMetadata()) {
            Duration age = Duration.between(downloadJob.getCreatedAt(), timeProvider.now());
            if (age.toMinutes() > appProperties.metadataTimeoutMinutes()) {
                scheduleRetry(downloadJob, DownloadJobStatus.WAITING_METADATA, ErrorCode.QBITTORRENT_METADATA_TIMEOUT, "Metadata is still unavailable");
            }
            return;
        }
        fileDiscoveryService.discoverFiles(downloadTarget(downloadJob), downloadJob.getId(), info.getHash());
        if (shouldAskFileSelection(downloadJob.getId())) {
            pauseTorrentForFileSelection(downloadJob, info.getHash());
            sendFileSelection(downloadJob);
            changeStatus(downloadJob, DownloadJobStatus.WAITING_FILE_SELECTION);
            return;
        }
        if (downloadTarget(downloadJob) == DownloadTarget.VPS) {
            failIfNotEnoughDiskSpace(downloadJob, requiredBytesForReadyFiles(downloadJob.getId()));
        }
        prepareTorrentFilePriorities(downloadJob.getId(), info.getHash());
        changeStatus(downloadJob, DownloadJobStatus.DOWNLOADING);
    }

    private void handleDownloading(DownloadJob downloadJob) {
        QbittorrentTorrentInfo info = findTorrent(downloadJob)
                .orElseThrow(() -> new RetryableOperationException(ErrorCode.QBITTORRENT_UNAVAILABLE, "Torrent not found"));
        int progressPercent = (int) Math.floor(info.getProgress() * 100);
        log.info("Torrent progress updated: jobId={}, hash={}, progress={}%, state={}", downloadJob.getId(), info.getHash(), progressPercent, info.getState());
        maybeSendProgress(downloadJob, info, progressPercent);
        if (info.getProgress() >= 1.0d) {
            sendDownloadCompletedMessage(downloadJob, info);
            changeStatus(downloadJob, DownloadJobStatus.DOWNLOAD_COMPLETED);
        }
    }

    private void handleDiscoveringFiles(DownloadJob downloadJob) {
        if (downloadFileRepository.findByJobId(downloadJob.getId()).isEmpty()) {
            fileDiscoveryService.discoverFiles(downloadTarget(downloadJob), downloadJob.getId(), requireTorrentHash(downloadJob));
        }
        sendDiscoverySummary(downloadJob);
        if (downloadTarget(downloadJob) == DownloadTarget.HOME_PC) {
            changeStatus(downloadJob, DownloadJobStatus.DELIVERY_COMPLETED);
            return;
        }
        changeStatus(downloadJob, DownloadJobStatus.DELIVERY_PENDING);
    }

    private void handleUploading(DownloadJob downloadJob) {
        FileDeliveryService.DeliveryResult deliveryResult = fileDeliveryService.deliverFiles(downloadJob.getId(), downloadJob.getChatId());
        if (deliveryResult.hasFinalFailure()) {
            failFinally(downloadJob, ErrorCode.TELEGRAM_UPLOAD_FAILED, "One or more files failed permanently");
            return;
        }
        if (deliveryResult.hasRetryableFailure()) {
            scheduleRetry(downloadJob, DownloadJobStatus.UPLOADING_TO_TELEGRAM, ErrorCode.TELEGRAM_UPLOAD_FAILED, "Telegram upload failed temporarily");
            telegramMessageService.sendText(downloadJob.getChatId(), "Возникла временная ошибка, задача не отменена. Я продолжу попытки автоматически.");
            return;
        }
        changeStatus(downloadJob, DownloadJobStatus.DELIVERY_COMPLETED);
    }

    private void handleDeliveryCompleted(DownloadJob downloadJob) {
        if (downloadTarget(downloadJob) == DownloadTarget.HOME_PC) {
            handleFinished(downloadJob);
            return;
        }
        if (downloadJob.isDeleteAfterUpload()) {
            changeStatus(downloadJob, DownloadJobStatus.CLEANUP_PENDING);
            return;
        }
        handleFinished(downloadJob);
    }

    private void handleCleaningUp(DownloadJob downloadJob) {
        cleanupService.cleanup(downloadTarget(downloadJob), downloadJob.getId(), requireTorrentHash(downloadJob));
        changeStatus(downloadJob, DownloadJobStatus.CLEANUP_COMPLETED);
    }

    private void handleFinished(DownloadJob downloadJob) {
        LocalDateTime now = timeProvider.now();
        downloadJobRepository.markCompleted(downloadJob.getId(), now);
        int uploadedCount = countFilesByStatus(downloadJob.getId(), DownloadFileStatus.UPLOADED);
        int downloadLinkCount = countFilesByStatus(downloadJob.getId(), DownloadFileStatus.DOWNLOAD_LINK_CREATED);
        if (downloadTarget(downloadJob) == DownloadTarget.HOME_PC) {
            updateHomePcFinishedMessage(downloadJob);
            startNextQueuedJob();
            return;
        }
        String cleanupText = downloadJob.isDeleteAfterUpload() ? " Исходные файлы torrent на сервере удалены." : "";
        telegramMessageService.sendText(downloadJob.getChatId(),
                "Готово. Отправлено файлов: " + uploadedCount + ". Временных ссылок: " + downloadLinkCount + "." + cleanupText);
        startNextQueuedJob();
    }

    private void sendDiscoverySummary(DownloadJob downloadJob) {
        List<DownloadFile> files = downloadFileRepository.findByJobId(downloadJob.getId());
        int directSendCount = 0;
        int temporaryLinkCount = 0;
        for (DownloadFile file : files) {
            if (file.getStatus() != DownloadFileStatus.READY_TO_UPLOAD) {
                continue;
            }
            FileDeliveryMode deliveryMode = fileDeliveryDecisionService.decide(file.getSizeBytes());
            if (deliveryMode == FileDeliveryMode.TELEGRAM_DIRECT) {
                directSendCount++;
            } else {
                temporaryLinkCount++;
            }
        }
        int unsupportedCount = countFilesByStatus(files, DownloadFileStatus.SKIPPED_UNSUPPORTED);

        if (directSendCount > 0 || temporaryLinkCount > 0) {
            updateStatusMessage(
                    downloadJob,
                    "Загрузка завершена.\n"
                            + "Раздача: " + safeTorrentName(downloadJob) + "\n"
                            + "Куда скачано: " + targetLabel(downloadTarget(downloadJob)) + "\n"
                            + "Файлов для прямой отправки: " + directSendCount + "\n"
                            + "Файлов для WebDAV-медиатеки: " + temporaryLinkCount + "\n"
                            + "Пропущено из-за формата: " + unsupportedCount + "\n\n"
                            + nextStepText(downloadJob)
            );
            return;
        }

        String largestSkippedFile = largestSkippedFileText(files);
        updateStatusMessage(
                downloadJob,
                "Загрузка завершена, но отправлять нечего: подходящих видеофайлов не найдено."
                        + largestSkippedFile
                        + " Я очищу файлы на сервере автоматически."
        );
    }

    private boolean shouldAskFileSelection(UUID jobId) {
        List<DownloadFile> files = downloadFileRepository.findByJobIdAndStatuses(jobId, List.of(DownloadFileStatus.READY_TO_UPLOAD));
        return files.size() > 1;
    }

    private void sendFileSelection(DownloadJob downloadJob) {
        List<DownloadFile> files = downloadFileRepository.findByJobIdAndStatuses(downloadJob.getId(), List.of(DownloadFileStatus.READY_TO_UPLOAD));
        telegramMessageService.sendTextWithInlineKeyboard(
                downloadJob.getChatId(),
                fileSelectionViewFactory.text(files, 0),
                fileSelectionViewFactory.keyboard(files, downloadJob.getId(), 0)
        );
    }

    private String largestSkippedFileText(List<DownloadFile> files) {
        DownloadFile largestFile = null;
        for (DownloadFile file : files) {
            if (largestFile == null || file.getSizeBytes() > largestFile.getSizeBytes()) {
                largestFile = file;
            }
        }
        if (largestFile == null) {
            return "";
        }
        return " Самый большой файл: " + largestFile.getFileName()
                + " (" + fileSizeFormatter.format(largestFile.getSizeBytes()) + ").";
    }

    private int countFilesByStatus(UUID jobId, DownloadFileStatus status) {
        return countFilesByStatus(downloadFileRepository.findByJobId(jobId), status);
    }

    private int countFilesByStatus(List<DownloadFile> files, DownloadFileStatus status) {
        int count = 0;
        for (DownloadFile file : files) {
            if (file.getStatus() == status) {
                count++;
            }
        }
        return count;
    }

    private Optional<QbittorrentTorrentInfo> findTorrent(DownloadJob downloadJob) {
        DownloadTarget downloadTarget = downloadTarget(downloadJob);
        if (downloadJob.getTorrentHash() != null && !downloadJob.getTorrentHash().isBlank()) {
            Optional<QbittorrentTorrentInfo> byHash = qbittorrentTorrentService.getTorrentInfoByHash(downloadTarget, downloadJob.getTorrentHash());
            if (byHash.isPresent()) {
                return byHash;
            }
        }
        Optional<QbittorrentTorrentInfo> byTag = qbittorrentTorrentService.getTorrentInfoByJobTag(downloadTarget, downloadJob.getId());
        byTag.ifPresent(info -> {
            downloadJobRepository.updateTorrentIdentity(downloadJob.getId(), info.getHash(), info.getName());
            log.info("Recovered torrent hash after restart: jobId={}, torrentHash={}, torrentName={}, downloadTarget={}", downloadJob.getId(), info.getHash(), info.getName(), downloadTarget);
        });
        return byTag;
    }

    private void maybeSendProgress(DownloadJob downloadJob, QbittorrentTorrentInfo info, int progressPercent) {
        int reportedStep = (progressPercent / 10) * 10;
        boolean firstProgressReport = downloadJob.getLastReportedProgressPercent() < 0;
        boolean nextProgressStep = reportedStep >= downloadJob.getLastReportedProgressPercent() + 10;
        if (!firstProgressReport && !nextProgressStep) {
            return;
        }

        updateStatusMessage(downloadJob, progressText(downloadJob, info, progressPercent));
        if (reportedStep >= 0) {
            downloadJobRepository.updateLastReportedProgress(downloadJob.getId(), reportedStep);
        }
    }

    private String progressText(DownloadJob downloadJob, QbittorrentTorrentInfo info, int progressPercent) {
        StringBuilder text = new StringBuilder();
        text.append("Скачиваю: ").append(progressPercent).append("%\n");
        text.append("Куда: ").append(targetLabel(downloadTarget(downloadJob))).append("\n");
        text.append("Раздача: ").append(preferredTorrentName(downloadJob, info.getName())).append("\n");
        if (info.getTotalSize() > 0) {
            text.append("Скачано: ")
                    .append(fileSizeFormatter.format(info.getDownloaded()))
                    .append(" из ")
                    .append(fileSizeFormatter.format(info.getTotalSize()))
                    .append("\n");
        }
        text.append("Скорость: ").append(fileSizeFormatter.format(info.getDownloadSpeed())).append("/s\n");
        text.append("Осталось: ").append(fileSizeFormatter.format(info.getAmountLeft())).append("\n");
        text.append("Времени примерно: ").append(formatEta(info.getEta())).append("\n");
        String finishTime = formatFinishTime(info.getEta());
        if (!finishTime.isBlank()) {
            text.append("Примерно закончит: ").append(finishTime).append("\n");
        }
        if (downloadTarget(downloadJob) == DownloadTarget.VPS) {
            DiskSpaceService.DiskSpaceInfo diskSpaceInfo = diskSpaceService.downloadStorageInfo();
            text.append("Свободно на сервере: ").append(fileSizeFormatter.format(diskSpaceInfo.usableBytes()));
        } else {
            text.append("Файл пишется сразу на домашний компьютер.");
        }
        return text.toString();
    }

    private String formatEta(long etaSeconds) {
        if (etaSeconds <= 0 || etaSeconds >= 8_640_000L) {
            return "считаю";
        }
        Duration duration = Duration.ofSeconds(etaSeconds);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0) {
            return hours + " ч " + minutes + " мин";
        }
        return Math.max(1, minutes) + " мин";
    }

    private String formatFinishTime(long etaSeconds) {
        if (etaSeconds <= 0 || etaSeconds >= 8_640_000L) {
            return "";
        }
        return timeProvider.zonedNow()
                .plusSeconds(etaSeconds)
                .format(DateTimeFormatter.ofPattern("HH:mm 'МСК'"));
    }

    private void sendDownloadCompletedMessage(DownloadJob downloadJob, QbittorrentTorrentInfo info) {
        String torrentName = preferredTorrentName(downloadJob, info.getName());
        StringBuilder text = new StringBuilder();
        text.append("Загрузка завершена.\n");
        text.append("Фильм: ").append(torrentName).append("\n");
        if (info.getTotalSize() > 0) {
            text.append("Размер: ").append(fileSizeFormatter.format(info.getTotalSize())).append("\n");
        }
        text.append("Куда скачано: ").append(targetLabel(downloadTarget(downloadJob))).append("\n\n");
        text.append(nextStepText(downloadJob));
        telegramMessageService.sendText(downloadJob.getChatId(), text.toString());
    }

    private void updateStatusMessage(DownloadJob downloadJob, String text) {
        if (downloadJob.getStatusMessageId() == null) {
            telegramMessageService.sendText(downloadJob.getChatId(), text);
            return;
        }
        telegramMessageService.editText(downloadJob.getChatId(), downloadJob.getStatusMessageId(), text, null);
    }

    private void updateStatusMessage(DownloadJob downloadJob, String text, String keyboardJson) {
        if (downloadJob.getStatusMessageId() == null) {
            telegramMessageService.sendTextWithInlineKeyboard(downloadJob.getChatId(), text, keyboardJson);
            return;
        }
        telegramMessageService.editText(downloadJob.getChatId(), downloadJob.getStatusMessageId(), text, keyboardJson);
    }

    private void changeStatus(DownloadJob downloadJob, DownloadJobStatus newStatus) {
        log.info("Changing job status: jobId={}, oldStatus={}, newStatus={}", downloadJob.getId(), downloadJob.getStatus(), newStatus);
        downloadJobRepository.updateStatus(downloadJob.getId(), newStatus);
    }

    private void scheduleRetry(DownloadJob downloadJob, DownloadJobStatus resumeStatus, ErrorCode errorCode, String errorMessage) {
        int nextRetryCount = downloadJob.getRetryCount() + 1;
        LocalDateTime nextRetryAt = retryScheduleService.calculateNextRetryAt(timeProvider.now(), downloadJob.getRetryCount());
        downloadJobRepository.scheduleRetry(downloadJob.getId(), resumeStatus, errorCode, errorMessage, nextRetryCount, nextRetryAt);
        if (downloadTarget(downloadJob) == DownloadTarget.HOME_PC) {
            downloadJobRepository.updateTargetStatus(downloadJob.getId(), TargetStatus.WAITING, errorMessage);
            updateStatusMessage(downloadJob, homePcRetryText(errorCode, nextRetryAt));
        }
        log.warn("Scheduling retry: jobId={}, nextRetryAt={}, errorCode={}", downloadJob.getId(), nextRetryAt, errorCode);
    }

    private String homePcRetryText(ErrorCode errorCode, LocalDateTime nextRetryAt) {
        String reasonText = switch (errorCode) {
            case QBITTORRENT_UNAVAILABLE, QBITTORRENT_AUTH_FAILED, QBITTORRENT_ADD_FAILED ->
                    "Домашний компьютер сейчас недоступен или qBittorrent не отвечает.";
            case TELEGRAM_SEND_FAILED, TELEGRAM_UPLOAD_FAILED ->
                    "Telegram временно не принял служебное сообщение.";
            default -> "Возникла временная ошибка.";
        };
        return reasonText + "\n"
                + "Задача не отменена, я продолжу попытки автоматически.\n"
                + "Следующая попытка: " + timeProvider.formatTime(nextRetryAt);
    }

    private void failFinally(DownloadJob downloadJob, ErrorCode errorCode, String errorMessage) {
        downloadJobRepository.markFailed(downloadJob.getId(), errorCode, errorMessage, timeProvider.now());
        log.error("Job failed finally: jobId={}, chatId={}, status={}, errorCode={}, message={}",
                downloadJob.getId(), downloadJob.getChatId(), downloadJob.getStatus(), errorCode, errorMessage);
        if (errorCode == ErrorCode.INSUFFICIENT_DISK_SPACE) {
            telegramMessageService.sendText(downloadJob.getChatId(), errorMessage);
            startNextQueuedJob();
            return;
        }
        telegramMessageService.sendText(downloadJob.getChatId(), "Задача не завершилась за установленное время. Я остановил автоматические попытки.");
        startNextQueuedJob();
    }

    private void pauseTorrentForFileSelection(DownloadJob downloadJob, String torrentHash) {
        List<Integer> fileIndexes = allDiscoveredFileIndexes(downloadJob.getId());
        qbittorrentTorrentService.setFilePriority(downloadTarget(downloadJob), torrentHash, fileIndexes, 0);
        qbittorrentTorrentService.pauseTorrent(downloadTarget(downloadJob), torrentHash);
        log.info("Torrent paused for file selection: jobId={}, torrentHash={}, files={}", downloadJob.getId(), torrentHash, fileIndexes.size());
    }

    private void prepareTorrentFilePriorities(UUID jobId, String torrentHash) {
        List<Integer> allFileIndexes = allDiscoveredFileIndexes(jobId);
        List<Integer> selectableFileIndexes = selectableFileIndexes(jobId);
        DownloadJob downloadJob = downloadJobRepository.findById(jobId).orElse(null);
        DownloadTarget downloadTarget = downloadJob == null ? DownloadTarget.VPS : downloadTarget(downloadJob);
        applyTorrentFilePriorities(downloadTarget, torrentHash, allFileIndexes, selectableFileIndexes);
        verifyTorrentFilePriorities(jobId, downloadTarget, torrentHash, allFileIndexes, selectableFileIndexes);
    }

    private void applyTorrentFilePriorities(DownloadTarget downloadTarget, String torrentHash, List<Integer> allFileIndexes, List<Integer> selectedFileIndexes) {
        qbittorrentTorrentService.setFilePriority(downloadTarget, torrentHash, allFileIndexes, 0);
        qbittorrentTorrentService.setFilePriority(downloadTarget, torrentHash, selectedFileIndexes, 1);
    }

    private void verifyTorrentFilePriorities(UUID jobId, DownloadTarget downloadTarget, String torrentHash, List<Integer> allFileIndexes, List<Integer> selectedFileIndexes) {
        List<QbittorrentTorrentFile> torrentFiles = qbittorrentTorrentService.getTorrentFiles(downloadTarget, torrentHash);
        List<Integer> unwantedActiveIndexes = unwantedActiveFileIndexes(torrentFiles, allFileIndexes, selectedFileIndexes);
        List<Integer> selectedDisabledIndexes = selectedDisabledFileIndexes(torrentFiles, allFileIndexes, selectedFileIndexes);

        if (unwantedActiveIndexes.isEmpty() && selectedDisabledIndexes.isEmpty()) {
            log.info("Torrent file priorities verified: jobId={}, torrentHash={}, selectedFiles={}", jobId, torrentHash, selectedFileIndexes);
            return;
        }

        log.warn("Torrent file priorities were not applied fully, retrying: jobId={}, torrentHash={}, unwantedActiveIndexes={}, selectedDisabledIndexes={}",
                jobId, torrentHash, unwantedActiveIndexes, selectedDisabledIndexes);
        applyTorrentFilePriorities(downloadTarget, torrentHash, allFileIndexes, selectedFileIndexes);
        List<QbittorrentTorrentFile> updatedTorrentFiles = qbittorrentTorrentService.getTorrentFiles(downloadTarget, torrentHash);
        List<Integer> stillUnwantedActiveIndexes = unwantedActiveFileIndexes(updatedTorrentFiles, allFileIndexes, selectedFileIndexes);
        List<Integer> stillSelectedDisabledIndexes = selectedDisabledFileIndexes(updatedTorrentFiles, allFileIndexes, selectedFileIndexes);
        if (!stillUnwantedActiveIndexes.isEmpty() || !stillSelectedDisabledIndexes.isEmpty()) {
            throw new RetryableOperationException(ErrorCode.QBITTORRENT_ADD_FAILED, "Could not apply selected torrent file priorities");
        }
        log.info("Torrent file priorities verified after retry: jobId={}, torrentHash={}, selectedFiles={}", jobId, torrentHash, selectedFileIndexes);
    }

    private List<Integer> unwantedActiveFileIndexes(List<QbittorrentTorrentFile> torrentFiles, List<Integer> allFileIndexes, List<Integer> selectedFileIndexes) {
        Set<Integer> discoveredIndexes = new HashSet<>(allFileIndexes);
        Set<Integer> selectedIndexes = new HashSet<>(selectedFileIndexes);
        List<Integer> unwantedActiveIndexes = new ArrayList<>();
        for (int fileIndex = 0; fileIndex < torrentFiles.size(); fileIndex++) {
            if (!discoveredIndexes.contains(fileIndex) || selectedIndexes.contains(fileIndex)) {
                continue;
            }
            QbittorrentTorrentFile torrentFile = torrentFiles.get(fileIndex);
            if (torrentFile.getPriority() != 0) {
                unwantedActiveIndexes.add(fileIndex);
            }
        }
        return unwantedActiveIndexes;
    }

    private List<Integer> selectedDisabledFileIndexes(List<QbittorrentTorrentFile> torrentFiles, List<Integer> allFileIndexes, List<Integer> selectedFileIndexes) {
        Set<Integer> discoveredIndexes = new HashSet<>(allFileIndexes);
        List<Integer> selectedDisabledIndexes = new ArrayList<>();
        for (Integer selectedFileIndex : selectedFileIndexes) {
            if (!discoveredIndexes.contains(selectedFileIndex) || selectedFileIndex < 0 || selectedFileIndex >= torrentFiles.size()) {
                continue;
            }
            QbittorrentTorrentFile torrentFile = torrentFiles.get(selectedFileIndex);
            if (torrentFile.getPriority() == 0) {
                selectedDisabledIndexes.add(selectedFileIndex);
            }
        }
        return selectedDisabledIndexes;
    }

    private List<Integer> selectableFileIndexes(UUID jobId) {
        return downloadFileRepository.findByJobIdAndStatuses(jobId, List.of(DownloadFileStatus.READY_TO_UPLOAD)).stream()
                .map(DownloadFile::getTorrentFileIndex)
                .filter(index -> index != null)
                .toList();
    }

    private List<Integer> allDiscoveredFileIndexes(UUID jobId) {
        return downloadFileRepository.findByJobId(jobId).stream()
                .map(DownloadFile::getTorrentFileIndex)
                .filter(index -> index != null)
                .toList();
    }

    public void startNextQueuedJob() {
        Optional<DownloadJob> queuedJob = downloadJobRepository.findNextQueued();
        if (queuedJob.isEmpty()) {
            return;
        }
        DownloadJob nextJob = queuedJob.get();
        log.info("Starting next queued job: jobId={}, chatId={}", nextJob.getId(), nextJob.getChatId());
        downloadJobRepository.updateStatus(nextJob.getId(), DownloadJobStatus.CREATED);
        if (nextJob.getStatusMessageId() == null) {
            telegramMessageService.sendText(nextJob.getChatId(), "Очередь дошла до этой задачи. Начинаю загрузку.");
        } else {
            telegramMessageService.editText(nextJob.getChatId(), nextJob.getStatusMessageId(), "Очередь дошла до этой задачи. Начинаю загрузку.", null);
        }
        processJob(nextJob.getId());
    }

    public void startQueuedJobs() {
        List<DownloadJob> queuedJobs = downloadJobRepository.findQueued(50);
        for (DownloadJob queuedJob : queuedJobs) {
            log.info("Starting legacy queued job: jobId={}, chatId={}", queuedJob.getId(), queuedJob.getChatId());
            downloadJobRepository.updateStatus(queuedJob.getId(), DownloadJobStatus.CREATED);
            processJob(queuedJob.getId());
        }
    }

    private void failIfJobTooOld(DownloadJob downloadJob) {
        Duration jobAge = Duration.between(downloadJob.getCreatedAt(), timeProvider.now());
        if (jobAge.toHours() > appProperties.maxJobAgeHours()) {
            throw new NonRetryableOperationException(ErrorCode.JOB_TIMEOUT, "Job exceeded max age");
        }
    }

    private void failIfNotEnoughDiskSpace(DownloadJob downloadJob, QbittorrentTorrentInfo info) {
        if (downloadTarget(downloadJob) != DownloadTarget.VPS) {
            return;
        }
        long bytesRequired = info.getAmountLeft() > 0 ? info.getAmountLeft() : info.getTotalSize();
        failIfNotEnoughDiskSpace(downloadJob, bytesRequired);
    }

    private void failIfNotEnoughDiskSpace(DownloadJob downloadJob, long bytesRequired) {
        if (bytesRequired <= 0 || diskSpaceService.hasEnoughSpace(bytesRequired)) {
            return;
        }
        DiskSpaceService.DiskSpaceInfo diskSpaceInfo = diskSpaceService.downloadStorageInfo();
        String message = "Не могу продолжить скачивание: на сервере мало свободного места.\n\n"
                + "Нужно примерно: " + fileSizeFormatter.format(bytesRequired) + "\n"
                + "Свободно: " + fileSizeFormatter.format(diskSpaceInfo.usableBytes()) + "\n\n"
                + "Очисти медиатеку или выбери раздачу меньшего размера.";
        throw new NonRetryableOperationException(ErrorCode.INSUFFICIENT_DISK_SPACE, message);
    }

    private long requiredBytesForReadyFiles(UUID jobId) {
        long bytesRequired = 0L;
        List<DownloadFile> readyFiles = downloadFileRepository.findByJobIdAndStatuses(jobId, List.of(DownloadFileStatus.READY_TO_UPLOAD));
        for (DownloadFile readyFile : readyFiles) {
            bytesRequired += readyFile.getSizeBytes();
        }
        return bytesRequired;
    }

    private String requireTorrentHash(DownloadJob downloadJob) {
        if (downloadJob.getTorrentHash() == null || downloadJob.getTorrentHash().isBlank()) {
            throw new RetryableOperationException(ErrorCode.QBITTORRENT_ADD_FAILED, "Torrent hash is not available");
        }
        return downloadJob.getTorrentHash();
    }

    private DownloadTarget downloadTarget(DownloadJob downloadJob) {
        return downloadJob.getDownloadTarget() == null ? DownloadTarget.VPS : downloadJob.getDownloadTarget();
    }

    private String targetLabel(DownloadTarget downloadTarget) {
        return switch (downloadTarget) {
            case HOME_PC -> "домашний ПК";
            case S3_LATER -> "S3";
            case VPS -> "VPS";
        };
    }

    private String safeTorrentName(DownloadJob downloadJob) {
        if (downloadJob.getTorrentName() == null || downloadJob.getTorrentName().isBlank()) {
            return "неизвестно";
        }
        return downloadJob.getTorrentName();
    }

    private String preferredTorrentName(DownloadJob downloadJob, String qbittorrentName) {
        if (downloadJob.getTorrentName() != null && !downloadJob.getTorrentName().isBlank()) {
            return downloadJob.getTorrentName();
        }
        if (qbittorrentName == null || qbittorrentName.isBlank()) {
            return "неизвестно";
        }
        return qbittorrentName;
    }

    private String nextStepText(DownloadJob downloadJob) {
        if (downloadTarget(downloadJob) == DownloadTarget.HOME_PC) {
            return "Файл уже лежит на домашнем компьютере. Сейчас завершаю задачу и покажу инструкцию для Infuse.";
        }
        return "Дальше: отправка в Telegram или добавление в WebDAV-медиатеку.";
    }

    private void updateHomePcFinishedMessage(DownloadJob downloadJob) {
        String keyboardJson = telegramKeyboardFactory.homePcFinishedKeyboard();
        updateStatusMessage(downloadJob, homePcFinishedTextWithLinks(downloadJob), keyboardJson);
    }

    private String homePcFinishedTextWithLinks(DownloadJob downloadJob) {
        List<DownloadFile> readyFiles = downloadFileRepository.findByJobIdAndStatuses(downloadJob.getId(), List.of(DownloadFileStatus.READY_TO_UPLOAD));
        StringBuilder text = new StringBuilder();
        text.append("Готово. Фильм скачан на домашний компьютер.\n");
        text.append("Раздача: ").append(safeTorrentName(downloadJob)).append("\n\n");
        if (readyFiles.isEmpty()) {
            text.append("Подходящих видеофайлов не найдено, но torrent завершен на домашнем qBittorrent.\n\n");
        } else {
            long totalBytes = 0L;
            for (DownloadFile file : readyFiles) {
                totalBytes += file.getSizeBytes();
            }
            text.append("Файлов: ").append(readyFiles.size()).append("\n");
            text.append("Общий размер: ").append(fileSizeFormatter.format(totalBytes)).append("\n\n");
            text.append("Файлы:\n");
            int index = 1;
            for (DownloadFile file : readyFiles) {
                if (index > 5) {
                    break;
                }
                text.append(index)
                        .append(". ")
                        .append(shortTelegramFileName(file.getFileName()))
                        .append(" - ")
                        .append(fileSizeFormatter.format(file.getSizeBytes()))
                        .append("\n");
                index++;
            }
            if (readyFiles.size() > 5) {
                text.append("... Рё РµС‰С‘ ").append(readyFiles.size() - 5).append(" С„Р°Р№Р».\n");
            }
            text.append("\n");
        }
        if (homeWebdavMediaLibraryService.isEnabled()) {
            text.append("WebDAV через Tailscale:\n")
                    .append(homeWebdavMediaLibraryService.baseUrl())
                    .append("\n");
            if (!homeWebdavMediaLibraryService.localBaseUrl().isBlank()) {
                text.append("\nWebDAV дома по Wi-Fi:\n")
                        .append(homeWebdavMediaLibraryService.localBaseUrl())
                        .append("\n");
            }
        } else {
            text.append("Домашний WebDAV выключен в конфиге бота.\n");
        }
        text.append("\nНа iPhone открой Infuse и скачай фильм офлайн через WebDAV. ")
                .append("Для телевизора дома используй Wi-Fi адрес, если телевизор в той же сети.");
        return limitTelegramText(text.toString());
    }
    private String shortTelegramFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "РІРёРґРµРѕС„Р°Р№Р»";
        }
        if (fileName.length() <= 90) {
            return fileName;
        }
        return fileName.substring(0, 87) + "...";
    }

    private String limitTelegramText(String text) {
        int maxLength = 3500;
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private String homePcFinishedText(DownloadJob downloadJob) {
        List<DownloadFile> readyFiles = downloadFileRepository.findByJobIdAndStatuses(downloadJob.getId(), List.of(DownloadFileStatus.READY_TO_UPLOAD));
        StringBuilder text = new StringBuilder();
        text.append("Готово. Фильм скачан на домашний компьютер.\n");
        text.append("Раздача: ").append(safeTorrentName(downloadJob)).append("\n\n");
        if (readyFiles.isEmpty()) {
            text.append("Подходящих видеофайлов не найдено, но torrent завершен на домашнем qBittorrent.\n\n");
        } else {
            text.append("Файлы:\n");
            int index = 1;
            for (DownloadFile file : readyFiles) {
                text.append(index)
                        .append(". ")
                        .append(file.getFileName())
                        .append(" — ")
                        .append(fileSizeFormatter.format(file.getSizeBytes()))
                        .append("\n");
                index++;
            }
            text.append("\n");
        }
        text.append("На iPhone открой Infuse, подключи WebDAV-папку домашнего ПК через Tailscale и нажми Download, чтобы сохранить фильм офлайн.");
        return text.toString();
    }

}

