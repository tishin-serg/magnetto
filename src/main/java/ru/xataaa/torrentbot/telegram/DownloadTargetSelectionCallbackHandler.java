package ru.xataaa.torrentbot.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.DiskSpaceService;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.job.DownloadJobService;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;

@Component
@RequiredArgsConstructor
public class DownloadTargetSelectionCallbackHandler implements TelegramCallbackHandler {

    private static final String PREFIX = "target:select:";

    private final DownloadTargetSelectionCache downloadTargetSelectionCache;
    private final DownloadJobService downloadJobService;
    private final TelegramMessageService telegramMessageService;
    private final S3MediaLibraryService s3MediaLibraryService;
    private final DiskSpaceService diskSpaceService;
    private final FileSizeFormatter fileSizeFormatter;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith(PREFIX);
    }

    @Override
    public void handle(String callbackQueryId, Long chatId, Long messageId, String data) {
        String[] parts = data.substring(PREFIX.length()).split(":");
        if (parts.length != 2) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Не понял выбор");
            return;
        }
        String selectionId = parts[0];
        DownloadTargetSelectionCache.PendingDownload pendingDownload = downloadTargetSelectionCache.find(selectionId, chatId).orElse(null);
        if (pendingDownload == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Выбор устарел");
            telegramMessageService.editText(chatId, messageId, "Этот выбор устарел. Проверь загрузки перед повторным запуском.", new TelegramKeyboardFactory().mainMenuKeyboard());
            return;
        }
        if (parts[1].equals("CANCEL")) {
            if (!downloadTargetSelectionCache.consume(selectionId, pendingDownload)) {
                telegramMessageService.answerCallbackQuery(callbackQueryId, "Заявка уже обработана. Проверь загрузки.");
                return;
            }
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Заявка отменена");
            telegramMessageService.editText(chatId, messageId, "Заявка отменена. Загрузка не запускалась.", new TelegramKeyboardFactory().mainMenuKeyboard());
            return;
        }
        DownloadTarget downloadTarget = DownloadTarget.fromValue(parts[1]);
        if (downloadTarget.isS3() && !isS3Ready()) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "S3 не настроен");
            telegramMessageService.editText(chatId, messageId,
                    "Облако S3 недоступно. Выбери домашний ПК или сервер VPS.",
                    DownloadTargetSelectionService.keyboard(selectionId));
            return;
        }
        if (!hasEnoughSpace(callbackQueryId, chatId, messageId, selectionId, pendingDownload, downloadTarget)) {
            return;
        }
        if (!downloadTargetSelectionCache.consume(selectionId, pendingDownload)) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Заявка уже обработана. Проверь загрузки.");
            return;
        }
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Запускаю загрузку");
        telegramMessageService.editText(chatId, messageId, "Выбрано: " + targetLabel(downloadTarget) + ". Создаю задачу...", null);
        downloadJobService.startDownload(chatId, pendingDownload.magnetUrl(), pendingDownload.expectedSizeBytes(), downloadTarget, pendingDownload.title());
    }

    private String targetLabel(DownloadTarget downloadTarget) {
        return switch (downloadTarget) {
            case HOME_PC -> "домашний ПК";
            case S3, S3_LATER -> "S3";
            case VPS -> "VPS";
        };
    }

    private boolean isS3Ready() {
        return s3MediaLibraryService.isEnabled() && s3MediaLibraryService.isConfigured();
    }

    private boolean hasEnoughSpace(String callbackQueryId, Long chatId, Long messageId, String selectionId,
            DownloadTargetSelectionCache.PendingDownload pendingDownload, DownloadTarget downloadTarget) {
        if (downloadTarget.isS3() || pendingDownload.expectedSizeBytes() <= 0) {
            return true;
        }
        try {
            DiskSpaceService.DiskSpaceInfo diskSpaceInfo = diskSpaceService.downloadStorageInfo(downloadTarget);
            if (diskSpaceInfo.usableBytes() >= pendingDownload.expectedSizeBytes()) {
                return true;
            }
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Недостаточно свободного места");
            telegramMessageService.editText(chatId, messageId,
                    "Не начинаю скачивание: файл больше свободного места в выбранной медиатеке.\n\n"
                            + "Размер файла: " + fileSizeFormatter.format(pendingDownload.expectedSizeBytes()) + "\n"
                            + "Свободно: " + fileSizeFormatter.format(diskSpaceInfo.usableBytes()) + "\n\n"
                            + "Освободи место, выбери другую медиатеку или раздачу меньшего размера.",
                    DownloadTargetSelectionService.keyboard(selectionId));
            return false;
        } catch (RuntimeException exception) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Не удалось проверить свободное место");
            telegramMessageService.editText(chatId, messageId,
                    "Не начинаю скачивание: не удалось проверить свободное место в выбранной медиатеке. Попробуй ещё раз.",
                    DownloadTargetSelectionService.keyboard(selectionId));
            return false;
        }
    }
}
