package com.yowyob.tiibntick.core.dispute.domain.exception;

/**
 * Thrown when an actor attempts an action on a dispute they are not authorized to perform.
 *
 * @author MANFOUO Braun
 */
public class DisputeAccessDeniedException extends RuntimeException {

    public DisputeAccessDeniedException(final String actorId, final String action, final String disputeId) {
        super("Actor [%s] is not authorized to perform [%s] on dispute [%s]"
                .formatted(actorId, action, disputeId));
    }
}
