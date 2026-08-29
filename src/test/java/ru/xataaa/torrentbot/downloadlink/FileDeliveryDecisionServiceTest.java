package ru.xataaa.torrentbot.downloadlink;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.config.TelegramProperties;

class FileDeliveryDecisionServiceTest {

    private final FileDeliveryDecisionService service = new FileDeliveryDecisionService(
            new TelegramProperties(
                    "token",
                    "bot",
                    "http://localhost:8081",
                    1000,
                    1000,
                    1000,
                        new TelegramProperties.FileProperties(100, 24, true)
            )
    );

    @Test
    void shouldSendDirectlyWhenFileIsSmallerThanLimit() {
        assertThat(service.decide(99)).isEqualTo(FileDeliveryMode.TELEGRAM_DIRECT);
    }

    @Test
    void shouldSendDirectlyWhenFileEqualsLimit() {
        assertThat(service.decide(100)).isEqualTo(FileDeliveryMode.TELEGRAM_DIRECT);
    }

    @Test
    void shouldCreateTemporaryLinkWhenFileIsBiggerThanLimit() {
        assertThat(service.decide(101)).isEqualTo(FileDeliveryMode.WEBDAV_LIBRARY);
    }
}
