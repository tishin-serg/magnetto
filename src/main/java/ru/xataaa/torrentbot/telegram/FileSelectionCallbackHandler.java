package ru.xataaa.torrentbot.telegram;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.DiskSpaceService;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.file.DownloadFile;
import ru.xataaa.torrentbot.file.DownloadFileRepository;
import ru.xataaa.torrentbot.file.DownloadFileStatus;
import ru.xataaa.torrentbot.job.DownloadJob;
import ru.xataaa.torrentbot.job.DownloadJobRepository;
import ru.xataaa.torrentbot.job.DownloadJobStatus;
import ru.xataaa.torrentbot.job.DownloadOrchestrator;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;
import ru.xataaa.torrentbot.qbittorrent.dto.QbittorrentTorrentFile;

@Component
@RequiredArgsConstructor
public class FileSelectionCallbackHandler implements TelegramCallbackHandler {

    private static final String ALL_PREFIX = "file:select:all:";
    private static final String DONE_PREFIX = "file:select:done:";
    private static final String PAGE_PREFIX = "file:select:page:";
    private static final String TOGGLE_PREFIX = "file:toggle:";
    private static final String LEGACY_ONE_PREFIX = "file:select:";

    private final DownloadFileRepository downloadFileRepository;
    private final DownloadJobRepository downloadJobRepository;
    private final DownloadOrchestrator downloadOrchestrator;
    private final TelegramMessageService telegramMessageService;
    private final QbittorrentTorrentService qbittorrentTorrentService;
    private final DiskSpaceService diskSpaceService;
    private final FileSizeFormatter fileSizeFormatter;
    private final FileSelectionViewFactory fileSelectionViewFactory;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith("file:");
    }

    @Override
    public void handle(String callbackQueryId, Long chatId, Long messageId, String data) {
        if (data.startsWith(PAGE_PREFIX)) {
            handlePage(callbackQueryId, chatId, messageId, data);
            return;
        }
        if (data.startsWith(TOGGLE_PREFIX)) {
            handleToggle(callbackQueryId, chatId, messageId, data.substring(TOGGLE_PREFIX.length()));
            return;
        }
        if (data.startsWith(DONE_PREFIX)) {
            handleDone(callbackQueryId, chatId, messageId, data.substring(DONE_PREFIX.length()));
            return;
        }
        if (data.startsWith(ALL_PREFIX)) {
            handleAll(callbackQueryId, chatId, messageId, data.substring(ALL_PREFIX.length()));
            return;
        }
        if (data.startsWith(LEGACY_ONE_PREFIX)) {
            handleLegacySingle(callbackQueryId, chatId, messageId, data.substring(LEGACY_ONE_PREFIX.length()));
        }
    }

    private void handleAll(String callbackQueryId, Long chatId, Long messageId, String jobIdValue) {
        DownloadJob downloadJob = findJob(jobIdValue);
        if (downloadJob == null || downloadJob.getTorrentHash() == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Задача не найдена");
            return;
        }
        List<DownloadFile> files = selectionFiles(downloadJob.getId());
        for (DownloadFile file : files) {
            downloadFileRepository.updateStatus(file.getId(), DownloadFileStatus.READY_TO_UPLOAD);
        }
        if (usesVpsStorage(downloadJob) && !hasEnoughSpace(chatId, messageId, sumSize(selectionFiles(downloadJob.getId())))) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Мало места на сервере");
            return;
        }
        startSelectedFiles(downloadJob, selectionFiles(downloadJob.getId()));
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Скачиваю все выбранные файлы");
        telegramMessageService.editText(chatId, messageId, "Выбрано: скачать все подходящие файлы. Продолжаю загрузку.", null);
    }

    private void handleDone(String callbackQueryId, Long chatId, Long messageId, String jobIdValue) {
        DownloadJob downloadJob = findJob(jobIdValue);
        if (downloadJob == null || downloadJob.getTorrentHash() == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Задача не найдена");
            return;
        }
        List<DownloadFile> selectedFiles = downloadFileRepository.findByJobIdAndStatuses(downloadJob.getId(), List.of(DownloadFileStatus.READY_TO_UPLOAD));
        if (selectedFiles.isEmpty()) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Выбери хотя бы один файл");
            return;
        }
        if (usesVpsStorage(downloadJob) && !hasEnoughSpace(chatId, messageId, sumSize(selectedFiles))) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Мало места на сервере");
            return;
        }
        startSelectedFiles(downloadJob, selectedFiles);
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Продолжаю загрузку");
        telegramMessageService.editText(
                chatId,
                messageId,
                selectedFilesConfirmationText(selectedFiles),
                null
        );
    }

    private void handleToggle(String callbackQueryId, Long chatId, Long messageId, String payload) {
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Не понял выбор");
            return;
        }
        UUID fileId = UUID.fromString(parts[0]);
        int page = parsePage(parts[1]);
        DownloadFile selectedFile = downloadFileRepository.findById(fileId).orElse(null);
        if (selectedFile == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Файл не найден");
            return;
        }
        DownloadFileStatus nextStatus = selectedFile.getStatus() == DownloadFileStatus.READY_TO_UPLOAD
                ? DownloadFileStatus.SKIPPED_BY_USER
                : DownloadFileStatus.READY_TO_UPLOAD;
        downloadFileRepository.updateStatus(selectedFile.getId(), nextStatus);
        List<DownloadFile> files = selectionFiles(selectedFile.getJobId());
        telegramMessageService.answerCallbackQuery(callbackQueryId, nextStatus == DownloadFileStatus.READY_TO_UPLOAD ? "Файл выбран" : "Файл исключён");
        telegramMessageService.editText(
                chatId,
                messageId,
                fileSelectionViewFactory.text(files, page),
                fileSelectionViewFactory.keyboard(files, selectedFile.getJobId(), page)
        );
    }

    private void handlePage(String callbackQueryId, Long chatId, Long messageId, String data) {
        String payload = data.substring(PAGE_PREFIX.length());
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Не понял страницу");
            return;
        }
        UUID jobId = UUID.fromString(parts[0]);
        int page = parsePage(parts[1]);
        List<DownloadFile> files = selectionFiles(jobId);
        if (files.isEmpty()) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Файлы уже выбраны или не найдены");
            return;
        }
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Страница " + (fileSelectionViewFactory.normalizePage(files, page) + 1));
        telegramMessageService.editText(
                chatId,
                messageId,
                fileSelectionViewFactory.text(files, page),
                fileSelectionViewFactory.keyboard(files, jobId, page)
        );
    }

    private void handleLegacySingle(String callbackQueryId, Long chatId, Long messageId, String fileIdValue) {
        if (fileIdValue.startsWith("all:") || fileIdValue.startsWith("done:") || fileIdValue.startsWith("page:")) {
            return;
        }
        DownloadFile selectedFile = downloadFileRepository.findById(UUID.fromString(fileIdValue)).orElse(null);
        if (selectedFile == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Файл не найден");
            return;
        }
        DownloadJob downloadJob = downloadJobRepository.findById(selectedFile.getJobId()).orElse(null);
        if (downloadJob == null || downloadJob.getTorrentHash() == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Задача не найдена");
            return;
        }
        for (DownloadFile file : selectionFiles(downloadJob.getId())) {
            DownloadFileStatus status = file.getId().equals(selectedFile.getId())
                    ? DownloadFileStatus.READY_TO_UPLOAD
                    : DownloadFileStatus.SKIPPED_BY_USER;
            downloadFileRepository.updateStatus(file.getId(), status);
        }
        if (usesVpsStorage(downloadJob) && !hasEnoughSpace(chatId, messageId, selectedFile.getSizeBytes())) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Мало места на сервере");
            return;
        }
        startSelectedFiles(downloadJob, List.of(selectedFile));
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Скачиваю выбранный файл");
        telegramMessageService.editText(chatId, messageId, selectedFilesConfirmationText(List.of(selectedFile)), null);
    }

    private void startSelectedFiles(DownloadJob downloadJob, List<DownloadFile> selectedFiles) {
        List<Integer> allFileIndexes = allFileIndexes(downloadJob.getId());
        List<Integer> selectedFileIndexes = selectedFileIndexes(selectedFiles);
        qbittorrentTorrentService.setFilePriority(downloadTarget(downloadJob), downloadJob.getTorrentHash(), allFileIndexes, 0);
        qbittorrentTorrentService.setFilePriority(downloadTarget(downloadJob), downloadJob.getTorrentHash(), selectedFileIndexes, 1);
        verifySelectedFilePriorities(downloadJob, allFileIndexes, selectedFileIndexes);
        qbittorrentTorrentService.resumeTorrent(downloadTarget(downloadJob), downloadJob.getTorrentHash());
        downloadJobRepository.updateStatus(downloadJob.getId(), DownloadJobStatus.DOWNLOADING);
        downloadOrchestrator.processJob(downloadJob.getId());
    }

    private void verifySelectedFilePriorities(DownloadJob downloadJob, List<Integer> allFileIndexes, List<Integer> selectedFileIndexes) {
        List<Integer> unwantedActiveIndexes = unwantedActiveFileIndexes(downloadJob, allFileIndexes, selectedFileIndexes);
        if (unwantedActiveIndexes.isEmpty()) {
            return;
        }
        qbittorrentTorrentService.setFilePriority(downloadTarget(downloadJob), downloadJob.getTorrentHash(), allFileIndexes, 0);
        qbittorrentTorrentService.setFilePriority(downloadTarget(downloadJob), downloadJob.getTorrentHash(), selectedFileIndexes, 1);
    }

    private List<Integer> unwantedActiveFileIndexes(DownloadJob downloadJob, List<Integer> allFileIndexes, List<Integer> selectedFileIndexes) {
        List<QbittorrentTorrentFile> torrentFiles = qbittorrentTorrentService.getTorrentFiles(downloadTarget(downloadJob), downloadJob.getTorrentHash());
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

    private List<DownloadFile> selectionFiles(UUID jobId) {
        return downloadFileRepository.findByJobIdAndStatuses(jobId, List.of(
                DownloadFileStatus.READY_TO_UPLOAD,
                DownloadFileStatus.SKIPPED_BY_USER
        ));
    }

    private List<Integer> selectedFileIndexes(List<DownloadFile> files) {
        return files.stream()
                .map(DownloadFile::getTorrentFileIndex)
                .filter(index -> index != null)
                .toList();
    }

    private List<Integer> allFileIndexes(UUID jobId) {
        return downloadFileRepository.findByJobId(jobId).stream()
                .map(DownloadFile::getTorrentFileIndex)
                .filter(index -> index != null)
                .toList();
    }

    private int parsePage(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException numberFormatException) {
            return 0;
        }
    }

    private long sumSize(List<DownloadFile> files) {
        long sizeBytes = 0L;
        for (DownloadFile file : files) {
            if (file.getStatus() == DownloadFileStatus.READY_TO_UPLOAD) {
                sizeBytes += file.getSizeBytes();
            }
        }
        return sizeBytes;
    }

    private String selectedFilesConfirmationText(List<DownloadFile> selectedFiles) {
        StringBuilder text = new StringBuilder();
        text.append("Подтверждено: скачиваю только выбранные файлы.\n\n");
        text.append("Файлов: ").append(selectedFiles.size()).append("\n");
        text.append("Размер: ").append(fileSizeFormatter.format(sumSize(selectedFiles))).append("\n\n");
        int index = 1;
        for (DownloadFile file : selectedFiles) {
            if (index > 5) {
                break;
            }
            text.append(index)
                    .append(". ")
                    .append(file.getFileName())
                    .append("\n");
            index++;
        }
        if (selectedFiles.size() > 5) {
            text.append("... и ещё ").append(selectedFiles.size() - 5).append(" файлов\n");
        }
        text.append("\nПродолжаю загрузку.");
        return text.toString();
    }

    private boolean hasEnoughSpace(Long chatId, Long messageId, long requiredBytes) {
        if (diskSpaceService.hasEnoughSpace(requiredBytes)) {
            return true;
        }
        DiskSpaceService.DiskSpaceInfo diskSpaceInfo = diskSpaceService.downloadStorageInfo();
        String text = "Не начинаю скачивание: выбранные файлы больше свободного места на сервере.\n\n"
                + "Нужно примерно: " + fileSizeFormatter.format(requiredBytes) + "\n"
                + "Свободно: " + fileSizeFormatter.format(diskSpaceInfo.usableBytes()) + "\n\n"
                + "Выбери меньше файлов или очисти медиатеку.";
        telegramMessageService.editText(chatId, messageId, text, null);
        return false;
    }

    private DownloadJob findJob(String jobIdValue) {
        return downloadJobRepository.findById(UUID.fromString(jobIdValue)).orElse(null);
    }

    private DownloadTarget downloadTarget(DownloadJob downloadJob) {
        return downloadJob.getDownloadTarget() == null ? DownloadTarget.VPS : downloadJob.getDownloadTarget();
    }

    private boolean usesVpsStorage(DownloadJob downloadJob) {
        DownloadTarget target = downloadTarget(downloadJob);
        return target == DownloadTarget.VPS || target.isS3();
    }
}
