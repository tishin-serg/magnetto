package ru.xataaa.torrentbot.retry;

import ru.xataaa.torrentbot.common.ErrorCode;

public class RetryableOperationException extends RuntimeException {

    private final ErrorCode errorCode;

    public RetryableOperationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RetryableOperationException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
