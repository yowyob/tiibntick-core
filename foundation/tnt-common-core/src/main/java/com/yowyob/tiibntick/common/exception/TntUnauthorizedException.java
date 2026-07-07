package com.yowyob.tiibntick.common.exception;

/**
 * Thrown when the caller lacks the permission to perform the requested operation.
 * Maps to HTTP 403 at the REST adapter layer.
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
public class TntUnauthorizedException extends TntException {

    public TntUnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }

    public TntUnauthorizedException(String errorCode, String message) {
        super(errorCode, message);
    }
}
