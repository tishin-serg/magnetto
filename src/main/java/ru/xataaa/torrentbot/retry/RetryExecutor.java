package ru.xataaa.torrentbot.retry;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.config.RetryProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetryExecutor {

    private final RetryProperties retryProperties;

    public <T> T execute(String operation, Supplier<T> operationSupplier) {
        RetryPolicy retryPolicy = RetryPolicy.fromProperties(retryProperties);
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            try {
                return operationSupplier.get();
            } catch (NonRetryableOperationException nonRetryableOperationException) {
                throw nonRetryableOperationException;
            } catch (RetryableOperationException retryableOperationException) {
                lastException = retryableOperationException;
                if (attempt == retryPolicy.maxAttempts()) {
                    break;
                }
                log.warn("Retrying operation: operation={}, attempt={}, maxAttempts={}, error={}",
                        operation, attempt, retryPolicy.maxAttempts(), sanitizeError(retryableOperationException.getMessage()));
                sleepBeforeRetry(retryPolicy.delayForAttempt(attempt));
            } catch (RuntimeException runtimeException) {
                lastException = runtimeException;
                if (attempt == retryPolicy.maxAttempts()) {
                    break;
                }
                log.warn("Retrying operation: operation={}, attempt={}, maxAttempts={}, error={}",
                        operation, attempt, retryPolicy.maxAttempts(), sanitizeError(runtimeException.getMessage()));
                sleepBeforeRetry(retryPolicy.delayForAttempt(attempt));
            }
        }

        throw lastException == null
                ? new RetryableOperationException(ru.xataaa.torrentbot.common.ErrorCode.UNKNOWN_ERROR, "Operation failed")
                : lastException;
    }

    public void executeVoid(String operation, Runnable runnable) {
        execute(operation, () -> {
            runnable.run();
            return Boolean.TRUE;
        });
    }

    private void sleepBeforeRetry(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RetryableOperationException(ru.xataaa.torrentbot.common.ErrorCode.UNKNOWN_ERROR, "Retry interrupted", interruptedException);
        }
    }

    private String sanitizeError(String message) {
        if (message == null) {
            return null;
        }
        return message
                .replaceAll("/bot[0-9]+:[A-Za-z0-9_-]+/", "/bot***/")
                .replaceAll("bot[0-9]+:[A-Za-z0-9_-]+", "bot***");
    }
}
