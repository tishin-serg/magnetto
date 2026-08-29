package ru.xataaa.torrentbot.telegram.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.telegram.TelegramKeyboardFactory;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;

@Component
@RequiredArgsConstructor
public class SettingsCommandHandler implements TelegramMessageHandler {

    private final TelegramMessageService telegramMessageService;
    private final TelegramKeyboardFactory telegramKeyboardFactory;

    @Override
    public boolean supports(String text) {
        return text != null && text.trim().startsWith("/settings");
    }

    @Override
    public void handle(Long chatId, String text) {
        telegramMessageService.sendTextWithInlineKeyboard(
                chatId,
                """
                        Настройки сейчас задаются на сервере через .env.

                        Доступные пользовательские действия:
                        • выбрать VPS или домашний ПК перед скачиванием;
                        • выбрать конкретные файлы внутри раздачи;
                        • открыть медиатеку;
                        • создать временную ссылку на iPhone.
                        """,
                telegramKeyboardFactory.mainMenuKeyboard()
        );
    }
}
