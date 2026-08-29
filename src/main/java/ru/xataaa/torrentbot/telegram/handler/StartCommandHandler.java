package ru.xataaa.torrentbot.telegram.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.telegram.MenuCallbackHandler;
import ru.xataaa.torrentbot.telegram.TelegramKeyboardFactory;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;

@Component
@RequiredArgsConstructor
public class StartCommandHandler implements TelegramMessageHandler {

    private final TelegramMessageService telegramMessageService;
    private final TelegramKeyboardFactory telegramKeyboardFactory;
    private final MenuCallbackHandler menuCallbackHandler;

    @Override
    public boolean supports(String text) {
        return text != null && text.trim().startsWith("/start");
    }

    @Override
    public void handle(Long chatId, String text) {
        telegramMessageService.sendTextWithInlineKeyboard(chatId, menuCallbackHandler.mainMenuText(), telegramKeyboardFactory.mainMenuKeyboard());
    }
}
