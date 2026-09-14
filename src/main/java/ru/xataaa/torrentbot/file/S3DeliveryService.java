package ru.xataaa.torrentbot.file;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.config.QbittorrentProperties;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.application.TemporaryDeliveryArtifactRepository;
import ru.xataaa.torrentbot.config.ShortcutApiProperties;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;
import ru.xataaa.torrentbot.media.S3UploadResult;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3DeliveryService {

    private static final List<DownloadFileStatus> DELIVERABLE_STATUSES = List.of(
            DownloadFileStatus.READY_TO_UPLOAD,
            DownloadFileStatus.UPLOAD_FAILED_RETRYABLE,
            DownloadFileStatus.UNKNOWN_UPLOAD_RESULT,
            DownloadFileStatus.UPLOADING_TO_S3,
            DownloadFileStatus.S3_UPLOADED
    );

    private final DownloadFileRepository downloadFileRepository;
    private final QbittorrentProperties qbittorrentProperties;
    private final S3MediaLibraryService s3MediaLibraryService;
    private final TelegramMessageService telegramMessageService;
    private final FileSizeFormatter fileSizeFormatter;
    private final ShortcutApiProperties shortcutApiProperties;
    private final TemporaryDeliveryArtifactRepository artifactRepository;

    public DeliveryResult deliverFiles(UUID jobId, Long chatId) {
        return deliverFiles(jobId, chatId, false);
    }

    public DeliveryResult deliverFiles(UUID jobId, Long chatId, boolean temporary) {
        List<DownloadFile> files = downloadFileRepository.findByJobIdAndStatuses(jobId, DELIVERABLE_STATUSES);
        int totalCount = files.size();
        boolean hasRetryableFailure = false;
        boolean hasFinalFailure = false;
        int deliveredCount = 0;

        for (DownloadFile downloadFile : files) {
            try {
                deliveredCount++;
                telegramMessageService.sendText(chatId, "Выгружаю в S3: " + deliveredCount + " из " + totalCount);
                deliverOne(downloadFile, temporary);
            } catch (NonRetryableOperationException exception) {
                hasFinalFailure = true;
                downloadFileRepository.incrementUploadAttempt(downloadFile.getId(), DownloadFileStatus.UPLOAD_FAILED_FINAL,
                        exception.getErrorCode(), exception.getMessage());
            } catch (RuntimeException exception) {
                hasRetryableFailure = true;
                downloadFileRepository.incrementUploadAttempt(downloadFile.getId(), DownloadFileStatus.UPLOAD_FAILED_RETRYABLE,
                        ErrorCode.S3_UPLOAD_FAILED, exception.getMessage());
            }
        }

        if (!hasRetryableFailure && !hasFinalFailure) {
            sendCompletion(chatId, downloadFileRepository.findByJobIdAndStatuses(jobId, List.of(DownloadFileStatus.S3_UPLOADED)), temporary);
        }
        int uploadedCount = (int) downloadFileRepository.findByJobIdAndStatuses(jobId, List.of(DownloadFileStatus.S3_UPLOADED)).size();
        return new DeliveryResult(uploadedCount, hasRetryableFailure, hasFinalFailure);
    }

    private void deliverOne(DownloadFile downloadFile, boolean temporary) {
        if (downloadFile.getStatus() == DownloadFileStatus.S3_UPLOADED
                && downloadFile.getS3ObjectKey() != null
                && !downloadFile.getS3ObjectKey().isBlank()) {
            return;
        }
        Path sourcePath = Path.of(qbittorrentProperties.target(DownloadTarget.VPS).downloadPath(), downloadFile.getRelativePath());
        if (!sourcePath.toFile().isFile()) {
            throw new NonRetryableOperationException(ErrorCode.FILE_NOT_FOUND, "File not found: " + downloadFile.getFileName());
        }
        downloadFileRepository.updateStatus(downloadFile.getId(), DownloadFileStatus.UPLOADING_TO_S3);
        S3UploadResult uploadResult = temporary ? s3MediaLibraryService.uploadTemporary(
                sourcePath, downloadFile.getFileName(), downloadFile.getS3ObjectKey(), downloadFile.getSizeBytes(), shortcutApiProperties.temporaryS3Prefix())
                : s3MediaLibraryService.upload(
                sourcePath,
                downloadFile.getFileName(),
                downloadFile.getS3ObjectKey(),
                downloadFile.getSizeBytes()
        );
        downloadFileRepository.markS3Uploaded(downloadFile.getId(), uploadResult.objectKey());
        if (temporary) artifactRepository.save(downloadFile.getJobId(), downloadFile.getId(), "S3", uploadResult.objectKey(),
                java.time.LocalDateTime.now().plusHours(shortcutApiProperties.temporaryTtlHours()));
        log.info("Delivered file to S3: jobId={}, fileName={}, objectKey={}, alreadyExists={}",
                downloadFile.getJobId(), downloadFile.getFileName(), uploadResult.objectKey(), uploadResult.alreadyExists());
    }

    private void sendCompletion(Long chatId, List<DownloadFile> files, boolean temporary) {
        StringBuilder text = new StringBuilder();
        text.append("Файлы выгружены в S3. Ссылки активны ")
                .append(s3MediaLibraryService.ttlHours())
                .append(" часов.\n\n");
        for (DownloadFile file : files) {
            if (file.getS3ObjectKey() == null || file.getS3ObjectKey().isBlank()) {
                continue;
            }
            text.append(file.getFileName())
                    .append("\n")
                    .append(fileSizeFormatter.format(file.getSizeBytes()))
                    .append("\n")
                    .append(temporary
                            ? s3MediaLibraryService.createTemporaryPresignedUrl(file.getS3ObjectKey(), shortcutApiProperties.temporaryS3Prefix())
                            : s3MediaLibraryService.createPresignedUrl(file.getS3ObjectKey()))
                    .append("\n\n");
        }
        telegramMessageService.sendText(chatId, text.toString().trim());
    }

    public record DeliveryResult(int uploadedCount, boolean hasRetryableFailure, boolean hasFinalFailure) {
    }
}
