package ru.xataaa.torrentbot.telegram;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.job.*;
import ru.xataaa.torrentbot.speed.*;

class DownloadSpeedCallbackHandlerTest {
    final DownloadJobRepository jobs = mock(DownloadJobRepository.class);
    final DownloadAlternativeRepository alternatives = mock(DownloadAlternativeRepository.class);
    final DownloadSpeedDecisionService decisions = mock(DownloadSpeedDecisionService.class);
    final DownloadSpeedMonitorRepository monitors = mock(DownloadSpeedMonitorRepository.class);
    final TelegramMessageService messages = mock(TelegramMessageService.class);
    final TimeProvider time = mock(TimeProvider.class);
    final DownloadSpeedCallbackHandler handler = new DownloadSpeedCallbackHandler(jobs, alternatives, decisions, monitors, messages, time);
    final UUID id = UUID.randomUUID();
    final DownloadJob job = DownloadJob.builder().id(id).chatId(42L).status(DownloadJobStatus.DOWNLOADING).build();
    final LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);

    @Test void foreignCallbackCannotSeeOrReplaceJob() {
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        handler.handle("q", 43L, 100L, "speed:list:" + id);
        verify(messages).answerCallbackQuery("q", "Задача не найдена");
        verifyNoInteractions(alternatives, decisions, monitors);
    }

    @Test void repeatedKeepIsIdempotent() {
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        when(time.now()).thenReturn(now);
        when(monitors.keepCurrent(id, now)).thenReturn(true, false);
        String callback = "speed:keep:" + id;

        handler.handle("q1", 42L, 100L, callback);
        handler.handle("q2", 42L, 100L, callback);

        verify(messages, times(1)).editText(eq(42L), eq(100L), contains("продолжится"), anyString());
        verify(messages).answerCallbackQuery("q2", "Выбор уже обработан или устарел");
    }

    @Test void repeatedChoiceCreatesOnlyOneReplacement() {
        DownloadAlternative alternative = new DownloadAlternative(0, "magnet:?xt=urn:btih:other", "Other", 10);
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        when(alternatives.findByJobId(id)).thenReturn(List.of(alternative));
        when(decisions.replace(job, alternative)).thenReturn(true, false);
        String callback = "speed:choose:" + id + ":0";

        handler.handle("q1", 42L, 100L, callback);
        handler.handle("q2", 42L, 100L, callback);

        verify(decisions, times(2)).replace(job, alternative);
        verify(messages, times(1)).editText(eq(42L), eq(100L), contains("Новая заявка создана"), anyString());
    }

    @Test void emptyAlternativesOfferNewSearch() {
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        when(alternatives.findByJobId(id)).thenReturn(List.of());
        handler.handle("q", 42L, 100L, "speed:list:" + id);
        verify(messages).editText(eq(42L), eq(100L), contains("альтернатив нет"), contains("menu:search"));
    }
}
