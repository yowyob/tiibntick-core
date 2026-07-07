package com.yowyob.tiibntick.common.exception;

/**
 * Thrown when a requested operation conflicts with the current resource state.
 * Maps to HTTP 409 at the REST adapter layer.
 * Examples: duplicate tracking code, mission already assigned, hub at capacity.
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
public class TntConflictException extends TntException {

    public TntConflictException(String message) {
        super("CONFLICT", message);
    }

    public TntConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
