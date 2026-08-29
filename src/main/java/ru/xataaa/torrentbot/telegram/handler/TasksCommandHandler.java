package ru.xataaa.torrentbot.telegram.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.telegram.TaskOverviewService;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;

@Component
@RequiredArgsConstructor
public class TasksCommandHandler implements TelegramMessageHandler {

    private final TelegramMessageService telegramMessageService;
    private final TaskOverviewService taskOverviewService;

    @Override
    public boolean supports(String text) {
        if (text == null) {
            return false;
        }
        String trimmedText = text.trim().toLowerCase();
        return trimmedText.startsWith("/tasks")
                || trimmedText.startsWith("/task")
                || trimmedText.startsWith("/status")
                || trimmedText.startsWith("/processes")
                || trimmedText.startsWith("/задачи");
    }

    @Override
    public void handle(Long chatId, String text) {
        telegramMessageService.sendTextWithInlineKeyboard(chatId, taskOverviewService.text(), taskOverviewService.keyboard());
    }
}
