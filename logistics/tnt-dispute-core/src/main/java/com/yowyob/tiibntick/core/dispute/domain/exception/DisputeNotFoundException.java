package com.yowyob.tiibntick.core.dispute.domain.exception;

import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

/**
 * Thrown when a requested dispute does not exist in the system.
 *
 * @author MANFOUO Braun
 */
public class DisputeNotFoundException extends RuntimeException {

    private final String disputeId;

    public DisputeNotFoundException(final DisputeId id) {
        super("Dispute not found: " + id.getValue());
        this.disputeId = id.getValue();
    }

    public DisputeNotFoundException(final String message) {
        super(message);
        this.disputeId = null;
    }

    public String getDisputeId() {
        return disputeId;
    }
}
