package com.yowyob.tiibntick.core.delivery.domain.model.enums;

import com.yowyob.tiibntick.core.delivery.domain.statemachine.AnnouncementStateMachine;

/**
 * State of a delivery announcement (spec §1.5). The transitions live in the single table
 * {@link AnnouncementStateMachine#TABLE} (rows A1–A7); {@link #isTerminal()} and {@link #isActive()} are derived
 * from it.
 */
public enum AnnouncementStatus {

    /** Saved, not visible to couriers. */
    DRAFT,
    /** Visible to eligible couriers. */
    PUBLISHED,
    /** At least one response received. */
    IN_NEGOTIATION,
    /** A response was selected; the delivery exists. */
    ASSIGNED,
    /** The delivery ended (delivered or finally failed: see the outcome). Final. */
    COMPLETED,
    /** Cancelled. Final. */
    CANCELLED;

    /** @return whether this state has no exit */
    public boolean isTerminal() {
        return AnnouncementStateMachine.TABLE.terminalStates().contains(this);
    }

    /** @return whether the announcement accepts responses and a selection (source states of row A4) */
    public boolean isActive() {
        return AnnouncementStateMachine.TABLE.row(AnnouncementStateMachine.A4).from().contains(this);
    }

    /** The wire value of this status (its name), as exposed to API clients. */
    public String getValue() {
        return name();
    }

    /**
     * Reads a status from its wire value, ignoring case.
     *
     * @throws IllegalArgumentException when the value names no status
     */
    public static AnnouncementStatus fromValue(String value) {
        for (AnnouncementStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown announcement status: " + value);
    }
}
