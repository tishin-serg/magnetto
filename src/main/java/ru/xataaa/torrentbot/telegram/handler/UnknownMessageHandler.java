package ru.xataaa.torrentbot.telegram.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;

@Component
@RequiredArgsConstructor
public class UnknownMessageHandler implements TelegramMessageHandler {

    private final TelegramMessageService telegramMessageService;

    @Override
    public boolean supports(String text) {
        return true;
    }

    @Override
    public void handle(Long chatId, String text) {
        telegramMessageService.sendText(chatId, "Не понял команду. Напиши название фильма, /search название или отправь magnet-ссылку.");
    }
}
