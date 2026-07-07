package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Status of a delivery person's response to a client announcement.
 *
 * @author MANFOUO Braun
 */
public enum ResponseStatus {

    /** Response submitted and waiting for client evaluation. */
    SENT,

    /** Client accepted this response and the delivery person is assigned. */
    ACCEPTED,

    /** Client rejected this offer. */
    REJECTED,

    /** Delivery person withdrew their offer before client decision. */
    CANCELLED
}
