package ru.xataaa.torrentbot.job;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RetryScheduleService {

    private static final List<Long> DELAYS_MINUTES = List.of(5L, 15L, 30L, 60L, 180L, 360L);

    public LocalDateTime calculateNextRetryAt(LocalDateTime now, int currentRetryCount) {
        int delayIndex = Math.max(0, Math.min(currentRetryCount, DELAYS_MINUTES.size() - 1));
        return now.plusMinutes(DELAYS_MINUTES.get(delayIndex));
    }
}
