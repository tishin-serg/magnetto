package ru.xataaa.torrentbot.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.media.S3MediaLibraryFile;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3MediaCallbackHandler implements TelegramCallbackHandler {

    private static final String FILE_PREFIX = "s3:file:";
    private static final String DOWNLOAD_PREFIX = "s3:download:";
    private static final String DELETE_ASK_PREFIX = "s3:delete:ask:";
    private static final String DELETE_CONFIRM_PREFIX = "s3:delete:confirm:";

    private final S3MediaLibraryService s3MediaLibraryService;
    private final TelegramMessageService telegramMessageService;
    private final TelegramKeyboardFactory telegramKeyboardFactory;
    private final FileSizeFormatter fileSizeFormatter;
    private final TimeProvider timeProvider;

    @Override
    public boolean supports(String data) {
        return data != null && (data.startsWith(FILE_PREFIX)
                || data.startsWith(DOWNLOAD_PREFIX)
                || data.startsWith(DELETE_ASK_PREFIX)
                || data.startsWith(DELETE_CONFIRM_PREFIX));
    }

    @Override
    public void handle(String callbackQueryId, Long chatId, Long messageId, String data) {
        try {
            if (data.startsWith(FILE_PREFIX)) {
                showFile(callbackQueryId, chatId, messageId, data.substring(FILE_PREFIX.length()));
                return;
            }
            if (data.startsWith(DOWNLOAD_PREFIX)) {
                createDownloadLink(callbackQueryId, chatId, messageId, data.substring(DOWNLOAD_PREFIX.length()));
                return;
            }
            if (data.startsWith(DELETE_ASK_PREFIX)) {
                askDelete(callbackQueryId, chatId, messageId, data.substring(DELETE_ASK_PREFIX.length()));
                return;
            }
            confirmDelete(callbackQueryId, chatId, messageId, data.substring(DELETE_CONFIRM_PREFIX.length()));
        } catch (RetryableOperationException exception) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "S3 временно недоступен");
            editOrSend(chatId, messageId, "S3 сейчас недоступен. Попробуй позже.", telegramKeyboardFactory.backToMenuKeyboard());
        }
    }

    private void showFile(String callbackQueryId, Long chatId, Long messageId, String fileKey) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Открываю файл");
        S3MediaLibraryFile file = findFile(fileKey, chatId, messageId);
        if (file == null) {
            return;
        }
        editOrSend(chatId, messageId, fileText(file), telegramKeyboardFactory.s3FileKeyboard(fileKey));
    }

    private void createDownloadLink(String callbackQueryId, Long chatId, Long messageId, String fileKey) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Создаю ссылку");
        S3MediaLibraryFile file = findFile(fileKey, chatId, messageId);
        if (file == null) {
            return;
        }
        String url = s3MediaLibraryService.createPresignedUrl(file.objectKey());
        String text = "Временная ссылка на S3 файл\n\n"
                + file.fileName() + "\n"
                + "Размер: " + fileSizeFormatter.format(file.sizeBytes()) + "\n"
                + "Активна: " + s3MediaLibraryService.ttlHours() + " часов.";
        editOrSend(chatId, messageId, text, telegramKeyboardFactory.s3FileDownloadKeyboard(fileKey, url));
    }

    private void askDelete(String callbackQueryId, Long chatId, Long messageId, String fileKey) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Нужно подтверждение");
        S3MediaLibraryFile file = findFile(fileKey, chatId, messageId);
        if (file == null) {
            return;
        }
        String text = "Удалить файл из S3 медиатеки?\n\n"
                + file.fileName() + "\n"
                + "Размер: " + fileSizeFormatter.format(file.sizeBytes()) + "\n\n"
                + "Будет удалён только этот объект внутри настроенного S3 prefix.";
        editOrSend(chatId, messageId, text, telegramKeyboardFactory.s3FileDeleteConfirmKeyboard(fileKey));
    }

    private void confirmDelete(String callbackQueryId, Long chatId, Long messageId, String fileKey) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Удаляю файл");
        S3MediaLibraryFile file = findFile(fileKey, chatId, messageId);
        if (file == null) {
            return;
        }
        s3MediaLibraryService.deleteFile(file.objectKey());
        log.info("Deleted S3 media file: chatId={}, objectKey={}, sizeBytes={}", chatId, file.objectKey(), file.sizeBytes());
        editOrSend(chatId, messageId, "Файл удалён из S3 медиатеки.\n\n" + file.fileName(), telegramKeyboardFactory.backToMenuKeyboard());
    }

    private S3MediaLibraryFile findFile(String fileKey, Long chatId, Long messageId) {
        S3MediaLibraryFile file = s3MediaLibraryService.findFileByKey(fileKey);
        if (file == null) {
            editOrSend(chatId, messageId, "Не нашёл этот файл в S3 медиатеке. Обнови список и попробуй ещё раз.", telegramKeyboardFactory.backToMenuKeyboard());
        }
        return file;
    }

    private String fileText(S3MediaLibraryFile file) {
        StringBuilder text = new StringBuilder();
        text.append("Файл в S3 медиатеке\n\n")
                .append(file.fileName())
                .append("\n\n")
                .append("Размер: ")
                .append(fileSizeFormatter.format(file.sizeBytes()))
                .append("\n");
        if (file.modifiedAt() != null) {
            text.append("Добавлен/изменён: ")
                    .append(timeProvider.formatDateTime(file.modifiedAt()))
                    .append("\n");
        }
        text.append("\nНажми «Скачать», чтобы создать новую временную ссылку.");
        return text.toString();
    }

    private void editOrSend(Long chatId, Long messageId, String text, String keyboardJson) {
        if (messageId == null) {
            telegramMessageService.sendTextWithInlineKeyboard(chatId, text, keyboardJson);
            return;
        }
        telegramMessageService.editText(chatId, messageId, text, keyboardJson);
    }
}
