package com.yowyob.tiibntick.core.gofreelancer.domain.exception;

/**
 * The handover code typed by the courier (pickup or delivery) does not match. Answered 400 with
 * the stable code {@value #CODE}, distinct from other bad requests and from conflicts.
 */
public class InvalidHandoverCodeException extends IllegalArgumentException {

    public static final String CODE = "INVALID_HANDOVER_CODE";

    public InvalidHandoverCodeException(String message) {
        super(message);
    }
}
