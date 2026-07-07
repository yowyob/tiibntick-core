package com.yowyob.tiibntick.core.dispute.domain.exception;

/**
 * Thrown when an operation is attempted that violates the dispute's state machine invariants.
 *
 * @author MANFOUO Braun
 */
public class DisputeStateException extends RuntimeException {

    public DisputeStateException(final String message) {
        super(message);
    }

    public DisputeStateException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
