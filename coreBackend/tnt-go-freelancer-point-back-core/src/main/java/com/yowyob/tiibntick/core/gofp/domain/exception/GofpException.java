package com.yowyob.tiibntick.core.gofp.domain.exception;

/**
 * Exception de base du module tnt-go-freelancer-point-back-core.
 */
public class GofpException extends RuntimeException {

    private final String errorCode;

    public GofpException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public GofpException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
