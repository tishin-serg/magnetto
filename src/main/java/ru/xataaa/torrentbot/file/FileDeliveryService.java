package ru.xataaa.torrentbot.file;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.config.QbittorrentProperties;
import ru.xataaa.torrentbot.downloadlink.DownloadLink;
import ru.xataaa.torrentbot.downloadlink.DownloadLinkService;
import ru.xataaa.torrentbot.downloadlink.FileDeliveryDecisionService;
import ru.xataaa.torrentbot.downloadlink.FileDeliveryMode;
import ru.xataaa.torrentbot.media.MediaLibraryResult;
import ru.xataaa.torrentbot.media.MediaLibraryService;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;
import ru.xataaa.torrentbot.retry.RetryableOperationException;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;
import ru.xataaa.torrentbot.telegram.dto.TelegramMessageResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeliveryService {

    private static final long DIRECT_UPLOAD_RETRY_FALLBACK_BYTES = 1_000_000_000L;

    private static final List<DownloadFileStatus> UPLOADABLE_STATUSES = List.of(
            DownloadFileStatus.READY_TO_UPLOAD,
            DownloadFileStatus.UPLOAD_FAILED_RETRYABLE,
            DownloadFileStatus.UNKNOWN_UPLOAD_RESULT
    );

    private final DownloadFileRepository downloadFileRepository;
    private final TelegramMessageService telegramMessageService;
    private final QbittorrentProperties qbittorrentProperties;
    private final FileDeliveryDecisionService fileDeliveryDecisionService;
    private final DownloadLinkService downloadLinkService;
    private final FileSizeFormatter fileSizeFormatter;
    private final MediaLibraryService mediaLibraryService;

    public DeliveryResult deliverFiles(UUID jobId, Long chatId) {
        List<DownloadFile> filesToUpload = downloadFileRepository.findByJobIdAndStatuses(jobId, UPLOADABLE_STATUSES);
        boolean hasRetryableFailure = false;
        boolean hasFinalFailure = false;

        for (DownloadFile downloadFile : filesToUpload) {
            try {
                uploadOneFile(chatId, downloadFile);
            } catch (NonRetryableOperationException nonRetryableOperationException) {
                hasFinalFailure = true;
                downloadFileRepository.incrementUploadAttempt(
                        downloadFile.getId(),
                        DownloadFileStatus.UPLOAD_FAILED_FINAL,
                        nonRetryableOperationException.getErrorCode(),
                        nonRetryableOperationException.getMessage()
                );
            } catch (RuntimeException runtimeException) {
                hasRetryableFailure = true;
                downloadFileRepository.incrementUploadAttempt(
                        downloadFile.getId(),
                        DownloadFileStatus.UPLOAD_FAILED_RETRYABLE,
                        ErrorCode.TELEGRAM_UPLOAD_FAILED,
                        runtimeException.getMessage()
                );
            }
        }

        int uploadedCount = (int) downloadFileRepository.findByJobId(jobId).stream()
                .filter(file -> file.getStatus() == DownloadFileStatus.UPLOADED)
                .count();
        int downloadLinkCount = (int) downloadFileRepository.findByJobId(jobId).stream()
                .filter(file -> file.getStatus() == DownloadFileStatus.DOWNLOAD_LINK_CREATED)
                .count();
        return new DeliveryResult(uploadedCount, downloadLinkCount, hasRetryableFailure, hasFinalFailure);
    }

    private void uploadOneFile(Long chatId, DownloadFile downloadFile) {
        File file = Path.of(qbittorrentProperties.downloadPath(), downloadFile.getRelativePath()).toFile();
        if (!file.exists() || !file.isFile()) {
            throw new NonRetryableOperationException(ErrorCode.FILE_NOT_FOUND, "File not found: " + downloadFile.getFileName());
        }

        FileDeliveryMode deliveryMode = fileDeliveryDecisionService.decide(downloadFile.getSizeBytes());
        if (deliveryMode == FileDeliveryMode.WEBDAV_LIBRARY) {
            deliverToWebdavLibrary(chatId, downloadFile, file.toPath());
            return;
        }

        log.info("Starting Telegram upload: jobId={}, fileName={}, sizeBytes={}",
                downloadFile.getJobId(), downloadFile.getFileName(), downloadFile.getSizeBytes());
        downloadFileRepository.updateStatus(downloadFile.getId(), DownloadFileStatus.UPLOADING);
        try {
            TelegramMessageResponse response = telegramMessageService.sendDocument(chatId, file, downloadFile.getFileName());
            downloadFileRepository.markUploaded(downloadFile.getId(), response.getMessageId());
        } catch (RetryableOperationException retryableOperationException) {
            if (downloadFile.getSizeBytes() >= DIRECT_UPLOAD_RETRY_FALLBACK_BYTES) {
                log.warn("Large Telegram upload failed, falling back to WebDAV: jobId={}, fileName={}, sizeBytes={}, error={}",
                        downloadFile.getJobId(), downloadFile.getFileName(), downloadFile.getSizeBytes(), retryableOperationException.getMessage());
                deliverToWebdavLibrary(chatId, downloadFile, file.toPath());
                return;
            }
            downloadFileRepository.updateStatusWithError(
                    downloadFile.getId(),
                    DownloadFileStatus.UNKNOWN_UPLOAD_RESULT,
                    ErrorCode.TELEGRAM_UPLOAD_UNKNOWN_RESULT,
                    retryableOperationException.getMessage()
            );
            throw retryableOperationException;
        } catch (RuntimeException runtimeException) {
            if (downloadFile.getSizeBytes() >= DIRECT_UPLOAD_RETRY_FALLBACK_BYTES) {
                log.warn("Large Telegram upload failed with ambiguous result, falling back to WebDAV: jobId={}, fileName={}, sizeBytes={}, error={}",
                        downloadFile.getJobId(), downloadFile.getFileName(), downloadFile.getSizeBytes(), runtimeException.getMessage());
                deliverToWebdavLibrary(chatId, downloadFile, file.toPath());
                return;
            }
            throw runtimeException;
        }
    }

    private void deliverToWebdavLibrary(Long chatId, DownloadFile downloadFile, Path sourcePath) {
        MediaLibraryResult mediaLibraryResult = mediaLibraryService.addToLibrary(sourcePath, downloadFile.getFileName());
        DownloadLink downloadLink = downloadLinkService.createDownloadLink(chatId, downloadFile);
        String text = """
                Файл слишком большой для прямой отправки в Telegram Bot API.

                Размер: %s

                Фильм добавлен в медиатеку WebDAV.

                На iPhone:
                1. Открой Infuse.
                2. Перейди в подключенную WebDAV-папку.
                3. Найди фильм: %s
                4. Нажми Download, чтобы сохранить его офлайн.
                5. Перед поездкой проверь, что фильм реально скачан локально.

                WebDAV:
                %s

                Временная ссылка как fallback активна %d часов.

                На iPhone удобнее скачивать через Documents by Readdle.
                Для просмотра можно использовать VLC или Infuse.
                """.formatted(
                fileSizeFormatter.format(downloadFile.getSizeBytes()),
                mediaLibraryResult.fileName(),
                mediaLibraryService.publicWebdavUrl(),
                downloadLinkService.ttlHours()
        );
        telegramMessageService.sendTextWithInlineKeyboard(chatId, text, inlineKeyboardJson(
                mediaLibraryService.publicWebdavUrl(),
                downloadLinkService.publicUrl(downloadLink)
        ));
        downloadFileRepository.updateStatus(downloadFile.getId(), DownloadFileStatus.DOWNLOAD_LINK_CREATED);
        log.info("Large file delivered through WebDAV library: jobId={}, fileName={}, sizeBytes={}, mediaFile={}, linkExpiresAt={}",
                downloadFile.getJobId(), downloadFile.getFileName(), downloadFile.getSizeBytes(), mediaLibraryResult.fileName(), downloadLink.getExpiresAt());
    }

    private String inlineKeyboardJson(String webdavUrl, String temporaryLinkUrl) {
        return """
                {"inline_keyboard":[
                  [{"text":"Открыть WebDAV-папку","url":"%s"}],
                  [{"text":"Скачать по временной ссылке","url":"%s"}]
                ]}
                """.formatted(escapeJson(webdavUrl), escapeJson(temporaryLinkUrl));
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record DeliveryResult(int uploadedCount, int downloadLinkCount, boolean hasRetryableFailure, boolean hasFinalFailure) {
    }
}
