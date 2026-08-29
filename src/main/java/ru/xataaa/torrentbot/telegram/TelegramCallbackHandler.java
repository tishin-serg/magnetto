package ru.xataaa.torrentbot.telegram;

public interface TelegramCallbackHandler {
    boolean supports(String data);

    void handle(String callbackQueryId, Long chatId, Long messageId, String data);

    default void handleInline(String callbackQueryId, String inlineMessageId, Long userId, String data) {
        handle(callbackQueryId, userId, null, data);
    }
}
