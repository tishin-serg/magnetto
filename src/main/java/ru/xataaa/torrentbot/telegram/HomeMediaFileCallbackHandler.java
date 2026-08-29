package ru.xataaa.torrentbot.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.downloadlink.HomeDownloadLinkService;
import ru.xataaa.torrentbot.media.HomeMediaLibraryFile;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Component
@RequiredArgsConstructor
public class HomeMediaFileCallbackHandler implements TelegramCallbackHandler {

    private static final String PREFIX = "home:file:";

    private final HomeDownloadLinkService homeDownloadLinkService;
    private final TelegramMessageService telegramMessageService;
    private final TelegramKeyboardFactory telegramKeyboardFactory;
    private final FileSizeFormatter fileSizeFormatter;
    private final TimeProvider timeProvider;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith(PREFIX);
    }

    @Override
    public void handle(String callbackQueryId, Long chatId, Long messageId, String data) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Открываю файл");
        String fileKey = data.substring(PREFIX.length());
        try {
            HomeMediaLibraryFile file = homeDownloadLinkService.findFileByKey(fileKey)
                    .orElse(null);
            if (file == null) {
                editOrSend(
                        chatId,
                        messageId,
                        "Не нашёл этот файл в домашней медиатеке. Нажми «Обновить» и попробуй ещё раз.",
                        telegramKeyboardFactory.backToMenuKeyboard()
                );
                return;
            }
            editOrSend(
                    chatId,
                    messageId,
                    fileText(file),
                    telegramKeyboardFactory.homeFileKeyboard(fileKey, file.tailscaleUrl(), file.localWifiUrl())
            );
        } catch (RetryableOperationException exception) {
            editOrSend(
                    chatId,
                    messageId,
                    "Домашний WebDAV сейчас недоступен. Проверь домашний ПК и Tailscale, потом обнови медиатеку.",
                    telegramKeyboardFactory.backToMenuKeyboard()
            );
        }
    }

    private String fileText(HomeMediaLibraryFile file) {
        StringBuilder text = new StringBuilder();
        text.append("Файл в домашней медиатеке\n\n")
                .append(file.fileName())
                .append("\n")
                .append(file.relativePath())
                .append("\n\n")
                .append("Размер: ")
                .append(fileSizeFormatter.format(file.sizeBytes()))
                .append("\n");
        if (file.modifiedAt() != null) {
            text.append("Добавлен/изменён: ")
                    .append(timeProvider.formatDateTime(file.modifiedAt()))
                    .append("\n");
        }
        text.append("\n")
                .append("Чтобы скачать на iPhone без WebDAV-пароля, нажми «Скачать на iPhone». ")
                .append("Я создам временную публичную ссылку на этот конкретный файл.\n\n")
                .append("Для больших фильмов стабильнее Infuse/WebDAV через Tailscale, но временная ссылка удобнее для разового скачивания.");
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
