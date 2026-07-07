package com.yowyob.tiibntick.common.exception;

/**
 * Root unchecked exception for all TiiBnTick Core domain and application errors.
 *
 * <p>Carry an {@code errorCode} field (UPPER_SNAKE_CASE) enabling programmatic
 * handling without string-matching on the message.
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
public class TntException extends RuntimeException {

    private final String errorCode;

    public TntException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TntException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** Returns the machine-readable error code (e.g., "MISSION_NOT_FOUND"). */
    public String getErrorCode() {
        return errorCode;
    }
}
