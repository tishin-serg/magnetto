package ru.xataaa.torrentbot.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RetryScheduleServiceTest {

    private final RetryScheduleService retryScheduleService = new RetryScheduleService();

    @Test
    void shouldCalculateRetryDelays() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);

        assertThat(retryScheduleService.calculateNextRetryAt(now, 0)).isEqualTo(now.plusMinutes(5));
        assertThat(retryScheduleService.calculateNextRetryAt(now, 1)).isEqualTo(now.plusMinutes(15));
        assertThat(retryScheduleService.calculateNextRetryAt(now, 2)).isEqualTo(now.plusMinutes(30));
        assertThat(retryScheduleService.calculateNextRetryAt(now, 3)).isEqualTo(now.plusHours(1));
        assertThat(retryScheduleService.calculateNextRetryAt(now, 4)).isEqualTo(now.plusHours(3));
        assertThat(retryScheduleService.calculateNextRetryAt(now, 5)).isEqualTo(now.plusHours(6));
        assertThat(retryScheduleService.calculateNextRetryAt(now, 50)).isEqualTo(now.plusHours(6));
    }
}
