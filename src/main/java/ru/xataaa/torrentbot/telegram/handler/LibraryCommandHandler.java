package ru.xataaa.torrentbot.telegram.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.telegram.MenuCallbackHandler;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;

@Component
@RequiredArgsConstructor
public class LibraryCommandHandler implements TelegramMessageHandler {

    private final TelegramMessageService telegramMessageService;
    private final MenuCallbackHandler menuCallbackHandler;

    @Override
    public boolean supports(String text) {
        return text != null && text.trim().startsWith("/library");
    }

    @Override
    public void handle(Long chatId, String text) {
        telegramMessageService.sendTextWithInlineKeyboard(chatId, menuCallbackHandler.mediaLibraryText(), menuCallbackHandler.mediaLibraryKeyboard());
    }
}
