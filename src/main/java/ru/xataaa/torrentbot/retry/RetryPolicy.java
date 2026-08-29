package ru.xataaa.torrentbot.retry;

import java.util.List;
import ru.xataaa.torrentbot.config.RetryProperties;

public record RetryPolicy(int maxAttempts, List<Long> delayMillis) {

    public static RetryPolicy fromProperties(RetryProperties retryProperties) {
        return new RetryPolicy(
                retryProperties.maxAttempts(),
                List.of(
                        retryProperties.initialDelayMs(),
                        retryProperties.secondDelayMs(),
                        retryProperties.maxDelayMs()
                )
        );
    }

    public long delayForAttempt(int attempt) {
        int delayIndex = Math.max(0, Math.min(attempt - 1, delayMillis.size() - 1));
        return delayMillis.get(delayIndex);
    }
}
