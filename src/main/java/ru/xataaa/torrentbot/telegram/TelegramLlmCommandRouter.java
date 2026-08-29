package ru.xataaa.torrentbot.telegram;

import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.config.AppProperties;
import ru.xataaa.torrentbot.config.LlmProperties;
import ru.xataaa.torrentbot.llm.LlmAction;
import ru.xataaa.torrentbot.llm.LlmRouteResult;
import ru.xataaa.torrentbot.llm.LlmRouter;
import ru.xataaa.torrentbot.llm.UserDialogState;
import ru.xataaa.torrentbot.llm.UserDialogStateRepository;
import ru.xataaa.torrentbot.telegram.handler.HelpCommandHandler;
import ru.xataaa.torrentbot.telegram.handler.LibraryCommandHandler;
import ru.xataaa.torrentbot.telegram.handler.SettingsCommandHandler;
import ru.xataaa.torrentbot.telegram.handler.TasksCommandHandler;
import ru.xataaa.torrentbot.telegram.handler.TelegramMessageHandler;
import ru.xataaa.torrentbot.telegram.handler.TorrentSearchMessageHandler;
import ru.xataaa.torrentbot.telegram.handler.UnknownMessageHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramLlmCommandRouter {

    private final LlmProperties llmProperties;
    private final AppProperties appProperties;
    private final LlmRouter llmRouter;
    private final UserDialogStateRepository userDialogStateRepository;
    private final TelegramCommandRouter legacyRouter;
    private final TorrentSearchMessageHandler torrentSearchMessageHandler;
    private final TasksCommandHandler tasksCommandHandler;
    private final LibraryCommandHandler libraryCommandHandler;
    private final HelpCommandHandler helpCommandHandler;
    private final SettingsCommandHandler settingsCommandHandler;
    private final UnknownMessageHandler unknownMessageHandler;
    private final MenuCallbackHandler menuCallbackHandler;
    private final MediaCleanupCallbackHandler mediaCleanupCallbackHandler;
    private final TelegramKeyboardFactory telegramKeyboardFactory;
    private final TelegramMessageService telegramMessageService;

    public void route(Long chatId, String text) {
        if (!llmProperties.enabled() || isLegacyText(text)) {
            legacyRouter.route(chatId, text);
            return;
        }
        if (!appProperties.isChatAllowed(chatId)) {
            unknownMessageHandler.handle(chatId, text);
            return;
        }
        UserDialogState state = userDialogStateRepository.find(chatId);
        LlmRouteResult routeResult = llmRouter.route(chatId, text, state);
        if (llmRouter.requiresFallback(routeResult, llmProperties.minConfidence())) {
            String action = routeResult == null ? "null" : routeResult.action().code();
            double confidence = routeResult == null ? 0.0 : routeResult.confidence();
            log.info("llm_router_fallback: chatId={}, action={}, confidence={}",
                    chatId, action, confidence);
            if (routeResult != null && routeResult.reply() != null && !routeResult.reply().isBlank()) {
                telegramMessageService.sendText(chatId, routeResult.reply());
                return;
            }
            helpCommandHandler.handle(chatId, "/help");
            return;
        }
        userDialogStateRepository.save(llmRouter.updateState(chatId, routeResult));
        dispatch(chatId, text, routeResult);
    }

    private boolean isLegacyText(String text) {
        if (text == null) {
            return true;
        }
        String trimmed = text.trim();
        return trimmed.startsWith("/") || trimmed.startsWith("magnet:");
    }

    private void dispatch(Long chatId, String originalText, LlmRouteResult routeResult) {
        log.info("llm_router_dispatch: chatId={}, action={}", chatId, routeResult.action().code());
        if (routeResult.action() == LlmAction.SHOW_FREE_SPACE) {
            telegramMessageService.sendTextWithInlineKeyboard(
                    chatId,
                    menuCallbackHandler.diskSpaceText(),
                    telegramKeyboardFactory.backToMenuKeyboard()
            );
            return;
        }
        if (routeResult.action() == LlmAction.CLEAR_LIBRARY) {
            telegramMessageService.sendTextWithInlineKeyboard(
                    chatId,
                    mediaCleanupCallbackHandler.cleanupAskText(),
                    telegramKeyboardFactory.cleanupConfirmKeyboard()
            );
            return;
        }
        if (routeResult.action() == LlmAction.SHOW_IPHONE_HELP) {
            telegramMessageService.sendTextWithInlineKeyboard(
                    chatId,
                    menuCallbackHandler.iphoneInstruction(),
                    telegramKeyboardFactory.backToMenuKeyboard()
            );
            return;
        }
        if (routeResult.action() == LlmAction.SEARCH_MEDIA && !llmRouter.hasSearchTitle(routeResult)) {
            if (routeResult.reply() != null && !routeResult.reply().isBlank()) {
                telegramMessageService.sendText(chatId, routeResult.reply());
                return;
            }
            helpCommandHandler.handle(chatId, "/help");
            return;
        }
        TelegramMessageHandler handler = switch (routeResult.action()) {
            case SEARCH_MEDIA -> torrentSearchMessageHandler;
            case SHOW_TASKS, PAUSE_TASK, RESUME_TASK -> tasksCommandHandler;
            case SHOW_LIBRARY -> libraryCommandHandler;
            case SHOW_HELP -> helpCommandHandler;
            case OPEN_SETTINGS -> settingsCommandHandler;
            case UNKNOWN, SHOW_FREE_SPACE, CLEAR_LIBRARY, SHOW_IPHONE_HELP -> unknownMessageHandler;
        };
        String handlerText = routeResult.action() == LlmAction.SEARCH_MEDIA
                ? searchQuery(routeResult.arguments(), originalText)
                : commandText(routeResult.action());
        handler.handle(chatId, handlerText);
    }

    private String commandText(LlmAction action) {
        return switch (action) {
            case SHOW_TASKS, PAUSE_TASK, RESUME_TASK -> "/tasks";
            case SHOW_LIBRARY -> "/library";
            case SHOW_HELP -> "/help";
            case OPEN_SETTINGS -> "/settings";
            default -> "";
        };
    }

    private String searchQuery(Map<String, Object> arguments, String originalText) {
        String title = stringArg(arguments, "title");
        if (title.isBlank()) {
            return originalText;
        }
        String suffix = arguments.entrySet().stream()
                .filter(entry -> !"title".equals(entry.getKey()) && !"media_type".equals(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.toString().isBlank())
                .map(Object::toString)
                .distinct()
                .collect(Collectors.joining(" "));
        return suffix.isBlank() ? title : title + " " + suffix;
    }

    private String stringArg(Map<String, Object> arguments, String key) {
        if (arguments == null || arguments.get(key) == null) {
            return "";
        }
        return arguments.get(key).toString().trim();
    }
}
