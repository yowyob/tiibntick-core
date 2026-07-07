package com.yowyob.tiibntick.core.sync.domain.exception;

public class SyncException extends RuntimeException {
    private final String errorCode;

    public SyncException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SyncException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
