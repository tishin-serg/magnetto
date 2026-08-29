package ru.xataaa.torrentbot.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.config.AppProperties;
import ru.xataaa.torrentbot.media.HomeWebdavCleanupService;
import ru.xataaa.torrentbot.media.MediaLibraryCleanupResult;
import ru.xataaa.torrentbot.media.MediaLibraryService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaCleanupCallbackHandler implements TelegramCallbackHandler {

    private final AppProperties appProperties;
    private final MediaLibraryService mediaLibraryService;
    private final HomeWebdavCleanupService homeWebdavCleanupService;
    private final FileSizeFormatter fileSizeFormatter;
    private final TelegramMessageService telegramMessageService;
    private final TelegramKeyboardFactory telegramKeyboardFactory;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith("media:cleanup:");
    }

    @Override
    public void handle(String callbackQueryId, Long chatId, Long messageId, String data) {
        if (!appProperties.isChatAllowed(chatId)) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Доступ запрещён");
            telegramMessageService.sendText(chatId, "Доступ запрещён.");
            return;
        }
        if ("media:cleanup:ask".equals(data)) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Нужно подтверждение");
            editOrSend(
                    chatId,
                    messageId,
                    "Что очистить?\n\nVPS — локальная WebDAV-медиатека на сервере.\nДомашняя медиатека — WebDAV-папка на домашнем ПК.",
                    telegramKeyboardFactory.cleanupConfirmKeyboard()
            );
            return;
        }
        if ("media:cleanup:confirm".equals(data) || "media:cleanup:confirm:local".equals(data)) {
            cleanupLocal(chatId, messageId, callbackQueryId);
            return;
        }
        if ("media:cleanup:confirm:home".equals(data)) {
            cleanupHome(chatId, messageId, callbackQueryId);
        }
    }

    private void cleanupLocal(Long chatId, Long messageId, String callbackQueryId) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Очищаю медиатеку VPS");
        MediaLibraryCleanupResult result = mediaLibraryService.cleanupAllFiles();
        log.info("Manual media cleanup completed: chatId={}, deletedFiles={}, deletedBytes={}",
                chatId, result.deletedFiles(), result.deletedBytes());
        editOrSend(
                chatId,
                messageId,
                "Медиатека VPS очищена.\nУдалено файлов: " + result.deletedFiles()
                        + "\nОсвобождено: " + fileSizeFormatter.format(result.deletedBytes()),
                telegramKeyboardFactory.backToMenuKeyboard()
        );
    }

    private void cleanupHome(Long chatId, Long messageId, String callbackQueryId) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Очищаю домашнюю медиатеку");
        try {
            MediaLibraryCleanupResult result = homeWebdavCleanupService.cleanupAllFiles();
            log.info("Manual home media cleanup completed: chatId={}, deletedFiles={}, deletedBytes={}",
                    chatId, result.deletedFiles(), result.deletedBytes());
            editOrSend(
                    chatId,
                    messageId,
                    "Домашняя медиатека очищена.\nУдалено файлов: " + result.deletedFiles()
                            + "\nРазмер WebDAV не сообщил, поэтому освобожденное место неизвестно.",
                    telegramKeyboardFactory.backToMenuKeyboard()
            );
        } catch (RuntimeException runtimeException) {
            log.warn("Manual home media cleanup failed: chatId={}, error={}", chatId, runtimeException.getMessage());
            editOrSend(
                    chatId,
                    messageId,
                    "Домашнюю медиатеку пока не удалось очистить.\nПроверь HOME_WEBDAV_ENABLED, HOME_WEBDAV_BASE_URL и доступность домашнего ПК через Tailscale.",
                    telegramKeyboardFactory.backToMenuKeyboard()
            );
        }
    }

    public String cleanupAskText() {
        return "Что очистить?\n\nVPS - локальная WebDAV-медиатека на сервере.\nДомашняя медиатека - WebDAV-папка на домашнем ПК.";
    }

    private void editOrSend(Long chatId, Long messageId, String text, String keyboardJson) {
        if (messageId == null) {
            telegramMessageService.sendTextWithInlineKeyboard(chatId, text, keyboardJson);
            return;
        }
        telegramMessageService.editText(chatId, messageId, text, keyboardJson);
    }
}
