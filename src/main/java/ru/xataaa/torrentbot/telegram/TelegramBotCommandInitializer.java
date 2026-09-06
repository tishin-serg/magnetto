package ru.xataaa.torrentbot.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotCommandInitializer implements ApplicationRunner {

    private final TelegramMessageService telegramMessageService;

    @Override
    public void run(ApplicationArguments args) {
        String commandsJson = """
                [
                  {"command":"start","description":"Главное меню"},
                  {"command":"search","description":"Найти фильм или сериал"},
                  {"command":"library","description":"Открыть медиатеку"},
                  {"command":"tasks","description":"Загрузки"},
                  {"command":"settings","description":"Настройки"},
                  {"command":"help","description":"Помощь"},
                  {"command":"cancel","description":"Выйти из ввода"}
                ]
                """;
        try {
            telegramMessageService.setMyCommands(commandsJson);
            log.info("Telegram bot commands configured");
        } catch (RuntimeException runtimeException) {
            log.warn("Telegram bot commands setup failed: error={}", runtimeException.getMessage());
        }
    }
}
