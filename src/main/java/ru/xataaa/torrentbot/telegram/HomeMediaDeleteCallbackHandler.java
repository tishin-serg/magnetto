package ru.xataaa.torrentbot.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.downloadlink.HomeDownloadLinkService;
import ru.xataaa.torrentbot.media.HomeMediaLibraryFile;
import ru.xataaa.torrentbot.media.HomeWebdavCleanupService;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomeMediaDeleteCallbackHandler implements TelegramCallbackHandler {

    private static final String ASK_PREFIX = "home:delete:ask:";
    private static final String CONFIRM_PREFIX = "home:delete:confirm:";
    private static final String CANCEL_PREFIX = "home:delete:cancel:";
    private final FileDeletionConfirmation confirmation = new FileDeletionConfirmation();

    private final HomeDownloadLinkService homeDownloadLinkService;
    private final HomeWebdavCleanupService homeWebdavCleanupService;
    private final TelegramMessageService telegramMessageService;
    private final TelegramKeyboardFactory telegramKeyboardFactory;
    private final FileSizeFormatter fileSizeFormatter;

    @Override
    public boolean supports(String data) {
        return data != null && (data.startsWith(ASK_PREFIX) || data.startsWith(CONFIRM_PREFIX) || data.startsWith(CANCEL_PREFIX));
    }

    @Override
    public void handle(String callbackQueryId, Long chatId, Long messageId, String data) {
        if (data.startsWith(CANCEL_PREFIX)) {
            confirmation.cancel(chatId, messageId, data.substring(CANCEL_PREFIX.length()));
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Удаление отменено");
            editOrSend(chatId, messageId, "Удаление отменено. Файл сохранён.", telegramKeyboardFactory.libraryRecoveryKeyboard("menu:library:home"));
            return;
        }
        if (data.startsWith(ASK_PREFIX)) {
            askDelete(callbackQueryId, chatId, messageId, data.substring(ASK_PREFIX.length()));
            return;
        }
        confirmDelete(callbackQueryId, chatId, messageId, data.substring(CONFIRM_PREFIX.length()));
    }

    private void askDelete(String callbackQueryId, Long chatId, Long messageId, String fileKey) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Нужно подтверждение");
        HomeMediaLibraryFile file = homeDownloadLinkService.findFileByKey(fileKey).orElse(null);
        if (file == null) {
            editOrSend(
                    chatId,
                    messageId,
                    "Не нашёл этот файл в домашней медиатеке. Обнови медиатеку и попробуй ещё раз.",
                    telegramKeyboardFactory.backToMenuKeyboard()
            );
            return;
        }
        confirmation.ask(chatId, messageId, fileKey, file);
        String text = "Удалить файл с домашнего ПК?\n\n"
                + file.relativePath() + "\n\n"
                + "Размер: " + fileSizeFormatter.format(file.sizeBytes()) + "\n\n"
                + "Это нельзя отменить.";
        editOrSend(chatId, messageId, text, telegramKeyboardFactory.homeFileDeleteConfirmKeyboard(fileKey));
    }

    private void confirmDelete(String callbackQueryId, Long chatId, Long messageId, String fileKey) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "");
        try {
            HomeMediaLibraryFile file = homeDownloadLinkService.findFileByKey(fileKey).orElse(null);
            if (file == null) {
                editOrSend(
                        chatId,
                        messageId,
                        "Файл уже не найден в домашней медиатеке.",
                        telegramKeyboardFactory.backToMenuKeyboard()
                );
                return;
            }
            if (!confirmation.consume(chatId, messageId, fileKey, file)) {
                editOrSend(chatId, messageId, "Подтверждение устарело или файл изменился. Открой файл заново перед удалением.",
                        telegramKeyboardFactory.libraryRecoveryKeyboard("menu:library:home"));
                return;
            }
            homeWebdavCleanupService.deleteFile(file.relativePath());
            log.info("Deleted home media file: chatId={}, fileName={}, relativePath={}, sizeBytes={}", chatId, file.fileName(), file.relativePath(), file.sizeBytes());
            editOrSend(
                    chatId,
                    messageId,
                    "Файл удалён из домашней медиатеки.\n\n" + file.relativePath(),
                    telegramKeyboardFactory.backToMenuKeyboard()
            );
        } catch (RetryableOperationException exception) {
            editOrSend(
                    chatId,
                    messageId,
                    "Не удалось удалить файл: домашний WebDAV сейчас недоступен. Попробуй позже.",
                    telegramKeyboardFactory.backToMenuKeyboard()
            );
        }
    }

    private void editOrSend(Long chatId, Long messageId, String text, String keyboardJson) {
        if (messageId == null) {
            telegramMessageService.sendTextWithInlineKeyboard(chatId, text, keyboardJson);
            return;
        }
        telegramMessageService.editText(chatId, messageId, text, keyboardJson);
    }
}
