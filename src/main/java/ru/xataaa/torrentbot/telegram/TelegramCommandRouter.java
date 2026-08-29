package ru.xataaa.torrentbot.telegram;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.telegram.handler.TelegramMessageHandler;
import ru.xataaa.torrentbot.telegram.handler.UnknownMessageHandler;

@Component
@RequiredArgsConstructor
public class TelegramCommandRouter {

    private final List<TelegramMessageHandler> handlers;
    private final UnknownMessageHandler unknownMessageHandler;

    public void route(Long chatId, String text) {
        List<TelegramMessageHandler> orderedHandlers = handlers.stream()
                .filter(handler -> !(handler instanceof UnknownMessageHandler))
                .sorted(Comparator.comparing(handler -> handler.getClass().getSimpleName()))
                .toList();
        for (TelegramMessageHandler handler : orderedHandlers) {
            if (handler.supports(text)) {
                handler.handle(chatId, text);
                return;
            }
        }
        unknownMessageHandler.handle(chatId, text);
    }
}
