package ru.xataaa.torrentbot.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.config.RetryProperties;

class RetryExecutorTest {

    @Test
    void shouldSucceedAfterTemporaryFailure() {
        RetryExecutor retryExecutor = new RetryExecutor(new RetryProperties(3, 1, 1, 1));
        AtomicInteger attempts = new AtomicInteger();

        String result = retryExecutor.execute("test", () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RetryableOperationException(ErrorCode.UNKNOWN_ERROR, "temporary");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldFailAfterAttemptsExhausted() {
        RetryExecutor retryExecutor = new RetryExecutor(new RetryProperties(3, 1, 1, 1));
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retryExecutor.execute("test", () -> {
            attempts.incrementAndGet();
            throw new RetryableOperationException(ErrorCode.UNKNOWN_ERROR, "temporary");
        })).isInstanceOf(RetryableOperationException.class);

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryNonRetryableFailure() {
        RetryExecutor retryExecutor = new RetryExecutor(new RetryProperties(3, 1, 1, 1));
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retryExecutor.execute("test", () -> {
            attempts.incrementAndGet();
            throw new NonRetryableOperationException(ErrorCode.INVALID_MAGNET, "bad");
        })).isInstanceOf(NonRetryableOperationException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }
}
