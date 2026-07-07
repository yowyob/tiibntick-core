package com.yowyob.tiibntick.core.realtime.domain.exception;

/**
 * Base unchecked exception for all tnt-realtime-core domain errors.
 *
 * @author MANFOUO Braun
 */
public class RealtimeException extends RuntimeException {

    private final String errorCode;

    public RealtimeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RealtimeException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
