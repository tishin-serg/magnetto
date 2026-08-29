package ru.xataaa.torrentbot.telegram;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.telegram.dto.TelegramUpdate;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPollingService {

    private final TelegramBotApiClient telegramBotApiClient;
    private final TelegramPollingStateRepository telegramPollingStateRepository;
    private final TelegramLlmCommandRouter telegramLlmCommandRouter;
    private final TelegramCallbackRouter telegramCallbackRouter;
    private final TelegramInlineQueryRouter telegramInlineQueryRouter;

    @Scheduled(fixedDelay = 1000)
    public void pollUpdates() {
        try {
            Long offset = telegramPollingStateRepository.getOffset().orElse(null);
            List<TelegramUpdate> updates = telegramBotApiClient.getUpdates(offset);
            updates.stream()
                    .sorted(Comparator.comparing(TelegramUpdate::getUpdateId))
                    .forEach(this::processUpdate);
        } catch (RuntimeException runtimeException) {
            log.warn("Telegram polling failed: error={}", runtimeException.getMessage());
        }
    }

    private void processUpdate(TelegramUpdate telegramUpdate) {
        try {
            if (telegramUpdate.getMessage() != null
                    && telegramUpdate.getMessage().getChat() != null
                    && telegramUpdate.getMessage().getText() != null) {
                telegramLlmCommandRouter.route(
                        telegramUpdate.getMessage().getChat().getId(),
                        telegramUpdate.getMessage().getText()
                );
                return;
            }
            if (telegramUpdate.getInlineQuery() != null
                    && telegramUpdate.getInlineQuery().getId() != null) {
                Long userId = telegramUpdate.getInlineQuery().getFrom() == null ? null : telegramUpdate.getInlineQuery().getFrom().getId();
                telegramInlineQueryRouter.route(
                        telegramUpdate.getInlineQuery().getId(),
                        userId,
                        telegramUpdate.getInlineQuery().getQuery()
                );
                return;
            }
            if (telegramUpdate.getCallbackQuery() != null
                    && telegramUpdate.getCallbackQuery().getData() != null) {
                if (telegramUpdate.getCallbackQuery().getInlineMessageId() != null) {
                    Long userId = telegramUpdate.getCallbackQuery().getFrom() == null ? null : telegramUpdate.getCallbackQuery().getFrom().getId();
                    telegramCallbackRouter.routeInline(
                            telegramUpdate.getCallbackQuery().getId(),
                            telegramUpdate.getCallbackQuery().getInlineMessageId(),
                            userId,
                            telegramUpdate.getCallbackQuery().getData()
                    );
                    return;
                }
                if (telegramUpdate.getCallbackQuery().getMessage() == null
                        || telegramUpdate.getCallbackQuery().getMessage().getChat() == null) {
                    return;
                }
                telegramCallbackRouter.route(
                        telegramUpdate.getCallbackQuery().getId(),
                        telegramUpdate.getCallbackQuery().getMessage().getChat().getId(),
                        telegramUpdate.getCallbackQuery().getMessage().getMessageId(),
                        telegramUpdate.getCallbackQuery().getData()
                );
            }
        } finally {
            telegramPollingStateRepository.saveOffset(telegramUpdate.getUpdateId() + 1);
        }
    }
}
