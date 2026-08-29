package ru.xataaa.torrentbot.retry;

import ru.xataaa.torrentbot.common.ErrorCode;

public class NonRetryableOperationException extends RuntimeException {

    private final ErrorCode errorCode;

    public NonRetryableOperationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public NonRetryableOperationException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
