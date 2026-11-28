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
    CANCELLED;

    /** The wire value of this status (its name), as exposed to API clients. */
    /** @return whether this state has no exit in {@code AnnouncementStateMachine.RESPONSE_TABLE} */
    public boolean isTerminal() {
        return com.yowyob.tiibntick.core.delivery.domain.statemachine.AnnouncementStateMachine.RESPONSE_TABLE
                .terminalStates().contains(this);
    }

    /** @return whether the response can still be selected, rejected or withdrawn */
    public boolean isActive() {
        return !isTerminal();
    }

    public String getValue() {
        return name();
    }

    /**
     * Reads a status from its wire value, ignoring case.
     *
     * @throws IllegalArgumentException when the value names no status
     */
    public static ResponseStatus fromValue(String value) {
        for (ResponseStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown response status: " + value);
    }
}
