package ru.xataaa.torrentbot.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.downloadlink.HomeDownloadLink;
import ru.xataaa.torrentbot.downloadlink.HomeDownloadLinkService;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Component
@RequiredArgsConstructor
public class HomeDownloadLinkCallbackHandler implements TelegramCallbackHandler {

    private static final String PREFIX = "home:link:";

    private final HomeDownloadLinkService homeDownloadLinkService;
    private final TelegramMessageService telegramMessageService;
    private final TelegramKeyboardFactory telegramKeyboardFactory;
    private final FileSizeFormatter fileSizeFormatter;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith(PREFIX);
    }

    @Override
    public void handle(String callbackQueryId, Long chatId, Long messageId, String data) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Создаю ссылку");
        String fileKey = data.substring(PREFIX.length());
        try {
            HomeDownloadLink homeDownloadLink = homeDownloadLinkService.createLink(chatId, fileKey);
            String publicUrl = homeDownloadLinkService.publicUrl(homeDownloadLink);
            String text = "Ссылка на скачивание готова.\n\n"
                    + "Файл: " + homeDownloadLink.getFileName() + "\n"
                    + "Размер: " + fileSizeFormatter.format(homeDownloadLink.getFileSizeBytes()) + "\n"
                    + "Ссылка активна: " + homeDownloadLinkService.ttlHours() + " ч.\n\n"
                    + publicUrl + "\n\n"
                    + "Эта ссылка не требует WebDAV-пароль. Если скачивание оборвётся, открой ссылку заново.";
            telegramMessageService.sendTextWithInlineKeyboard(
                    chatId,
                    text,
                    telegramKeyboardFactory.singleUrlKeyboard("Скачать файл", publicUrl)
            );
        } catch (NonRetryableOperationException exception) {
            telegramMessageService.sendText(chatId, "Не нашёл этот файл в домашней медиатеке. Обнови медиатеку и попробуй ещё раз.");
        } catch (RetryableOperationException exception) {
            telegramMessageService.sendText(chatId, "Домашний WebDAV сейчас недоступен. Проверь домашний ПК и попробуй чуть позже.");
        }
    }
}
