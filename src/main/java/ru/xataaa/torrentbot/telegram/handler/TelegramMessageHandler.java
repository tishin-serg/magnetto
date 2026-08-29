package ru.xataaa.torrentbot.telegram.handler;

public interface TelegramMessageHandler {
    boolean supports(String text);
    void handle(Long chatId, String text);
}
