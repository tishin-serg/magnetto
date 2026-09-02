package ru.xataaa.torrentbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.xataaa.torrentbot.common.FileSizeFormatter;

class DownloadTargetSelectionServiceTest {

    @Test
    void shouldOfferVpsHomePcAndS3Targets() {
        DownloadTargetSelectionCache cache = new DownloadTargetSelectionCache();
        TelegramMessageService telegramMessageService = mock(TelegramMessageService.class);
        DownloadTargetSelectionService service = new DownloadTargetSelectionService(cache, telegramMessageService, new FileSizeFormatter());

        service.askTarget(42L, "magnet:?xt=urn:btih:0123456789012345678901234567890123456789", 1024L, "Movie");

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyboardCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramMessageService).sendTextWithInlineKeyboard(eq(42L), textCaptor.capture(), keyboardCaptor.capture());
        assertThat(textCaptor.getValue()).contains("Сначала скачаю на VPS", "выгружу в S3");
        assertThat(keyboardCaptor.getValue()).contains(
                "target:select:",
                ":VPS",
                ":HOME_PC",
                ":S3",
                "☁️ Скачать в S3"
        );
    }
}
