package ru.xataaa.torrentbot.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.config.AppProperties;
import ru.xataaa.torrentbot.media.HomeWebdavCleanupService;
import ru.xataaa.torrentbot.media.MediaLibraryCleanupResult;
import ru.xataaa.torrentbot.media.MediaLibraryService;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaCleanupCallbackHandler implements TelegramCallbackHandler {

    private record Confirmation(Long chatId, Long messageId, String target, java.time.Instant expires) {}
    private final java.util.Map<String, Confirmation> confirmations = new java.util.concurrent.ConcurrentHashMap<>();

    public void cancelPending(Long chatId) {
        confirmations.entrySet().removeIf(e -> e.getValue().chatId().equals(chatId));
    }

    private final AppProperties appProperties;
    private final MediaLibraryService mediaLibraryService;
    private final HomeWebdavCleanupService homeWebdavCleanupService;
    private final S3MediaLibraryService s3MediaLibraryService;
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
                    cleanupAskText(),
                    telegramKeyboardFactory.cleanupConfirmKeyboard()
            );
            return;
        }
        if (data.startsWith("media:cleanup:confirm")) {
            String target = switch (data) {
                case "media:cleanup:confirm", "media:cleanup:confirm:local" -> "local";
                case "media:cleanup:confirm:home" -> "home";
                case "media:cleanup:confirm:s3" -> "s3";
                default -> null;
            };
            if (target == null) { telegramMessageService.answerCallbackQuery(callbackQueryId, "Открой очистку заново"); return; }
            confirmations.entrySet().removeIf(e -> e.getValue().expires().isBefore(java.time.Instant.now())
                    || e.getValue().chatId().equals(chatId));
            String token = java.util.UUID.randomUUID().toString().replace("-", "");
            confirmations.put(token, new Confirmation(chatId, messageId, target, java.time.Instant.now().plusSeconds(120)));
            telegramMessageService.answerCallbackQuery(callbackQueryId, "");
            String name = switch (target) { case "home" -> "домашнем ПК"; case "s3" -> "облаке S3"; default -> "сервере VPS"; };
            editOrSend(chatId, messageId, "Удалить все файлы медиатеки на " + name + "?\n\nЭто нельзя отменить. Для удаления одного фильма открой его в медиатеке.",
                    "{\"inline_keyboard\":[[{\"text\":\"🗑 Удалить все файлы\",\"callback_data\":\"media:cleanup:execute:" + token
                            + "\"}],[{\"text\":\"❌ Отмена\",\"callback_data\":\"media:cleanup:cancel:" + token + "\"}]]}");
            return;
        }
        if (data.startsWith("media:cleanup:cancel:") || data.startsWith("media:cleanup:execute:")) {
            String token = data.substring(data.lastIndexOf(':') + 1);
            Confirmation confirmation = confirmations.get(token);
            if (confirmation == null || !confirmation.chatId().equals(chatId)
                    || !java.util.Objects.equals(confirmation.messageId(), messageId)
                    || confirmation.expires().isBefore(java.time.Instant.now())
                    || !confirmations.remove(token, confirmation)) {
                telegramMessageService.answerCallbackQuery(callbackQueryId, "Подтверждение устарело. Открой очистку заново.");
                return;
            }
            if (data.startsWith("media:cleanup:cancel:")) {
                telegramMessageService.answerCallbackQuery(callbackQueryId, "Удаление отменено");
                editOrSend(chatId, messageId, "Удаление отменено. Файлы сохранены.", telegramKeyboardFactory.libraryMenuKeyboard());
                return;
            }
            switch (confirmation.target()) {
                case "home" -> cleanupHome(chatId, messageId, callbackQueryId);
                case "s3" -> cleanupS3(chatId, messageId, callbackQueryId);
                default -> cleanupLocal(chatId, messageId, callbackQueryId);
            }
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
                    "Не удалось завершить очистку. Домашний ПК недоступен.\n\nЧасть файлов могла быть удалена. Проверь медиатеку перед повторной попыткой.",
                    telegramKeyboardFactory.backToMenuKeyboard()
            );
        }
    }

    private void cleanupS3(Long chatId, Long messageId, String callbackQueryId) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Очищаю S3 медиатеку");
        try {
            MediaLibraryCleanupResult result = s3MediaLibraryService.cleanupAllFiles();
            log.info("Manual S3 media cleanup completed: chatId={}, deletedFiles={}, deletedBytes={}",
                    chatId, result.deletedFiles(), result.deletedBytes());
            editOrSend(
                    chatId,
                    messageId,
                    "S3 медиатека очищена.\nУдалено файлов: " + result.deletedFiles()
                            + "\nОсвобождено: " + fileSizeFormatter.format(result.deletedBytes()),
                    telegramKeyboardFactory.backToMenuKeyboard()
            );
        } catch (RuntimeException runtimeException) {
            log.warn("Manual S3 media cleanup failed: chatId={}, error={}", chatId, runtimeException.getMessage());
            editOrSend(
                    chatId,
                    messageId,
                    "Не удалось завершить очистку S3.\n\nЧасть файлов могла быть удалена. Проверь медиатеку перед повторной попыткой.",
                    telegramKeyboardFactory.backToMenuKeyboard()
            );
        }
    }

    public String cleanupAskText() {
        return "🗑 Очистка медиатеки\n\nВыбери хранилище. Перед удалением всех файлов я запрошу подтверждение.";
    }

    private void editOrSend(Long chatId, Long messageId, String text, String keyboardJson) {
        if (messageId == null) {
            telegramMessageService.sendTextWithInlineKeyboard(chatId, text, keyboardJson);
            return;
        }
        telegramMessageService.editText(chatId, messageId, text, keyboardJson);
    }
}
