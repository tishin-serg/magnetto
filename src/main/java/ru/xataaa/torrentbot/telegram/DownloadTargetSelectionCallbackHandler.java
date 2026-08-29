package ru.xataaa.torrentbot.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.job.DownloadJobService;
import ru.xataaa.torrentbot.job.DownloadTarget;

@Component
@RequiredArgsConstructor
public class DownloadTargetSelectionCallbackHandler implements TelegramCallbackHandler {

    private static final String PREFIX = "target:select:";

    private final DownloadTargetSelectionCache downloadTargetSelectionCache;
    private final DownloadJobService downloadJobService;
    private final TelegramMessageService telegramMessageService;

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
        DownloadTarget downloadTarget = DownloadTarget.fromValue(parts[1]);
        DownloadTargetSelectionCache.PendingDownload pendingDownload = downloadTargetSelectionCache.find(selectionId, chatId).orElse(null);
        if (pendingDownload == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Выбор устарел");
            telegramMessageService.editText(chatId, messageId, "Этот выбор устарел. Отправь magnet или выбери раздачу ещё раз.", null);
            return;
        }
        downloadTargetSelectionCache.remove(selectionId);
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Запускаю загрузку");
        telegramMessageService.editText(chatId, messageId, "Выбрано: " + targetLabel(downloadTarget) + ". Создаю задачу...", null);
        downloadJobService.startDownload(chatId, pendingDownload.magnetUrl(), pendingDownload.expectedSizeBytes(), downloadTarget, pendingDownload.title());
    }

    private String targetLabel(DownloadTarget downloadTarget) {
        return switch (downloadTarget) {
            case HOME_PC -> "домашний ПК";
            case S3_LATER -> "S3";
            case VPS -> "VPS";
        };
    }
}
