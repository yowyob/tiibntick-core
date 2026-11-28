package com.yowyob.tiibntick.core.delivery.domain.model.enums;

import com.yowyob.tiibntick.core.delivery.domain.statemachine.ParcelStateMachine;

import java.util.Set;

/**
 * State of a parcel across all its deliveries (spec §1.2, decisions [B.1], [B.2], [C bis]). Owned by delivery-core
 * and reused as is by trust (which only anchors it).
 *
 * <p>The transitions live in the single table {@link ParcelStateMachine#TABLE} (rows P1–P20); the parcel never
 * changes on its own: every row is caused by a delivery transition or by the dispute/return use cases.
 */
public enum ParcelStatus {

    /** Registered, no courier responsible (the real place is in the custody). */
    CREATED,
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    AT_RELAY_HUB,
    OUT_FOR_DELIVERY,
    DELIVERED,
    /** One hand-over attempt failed (delivery {@code DELIVERY_ATTEMPT_FAILED}). */
    DELIVERY_FAILED,
    /** A dispute is open; the previous state is memorised and restored if the dispute does not change it. */
    UNDER_DISPUTE,
    /** Back with the sender. Closed (only exit: a dispute within the filing window). */
    RETURNED,
    /** A return to the sender is required; a return delivery is expected. */
    RETURN_REQUESTED,
    /** Carried back to the sender by a return delivery. */
    RETURN_IN_TRANSIT,
    /** Lost, compensated. Final. */
    LOST,
    /** Damaged, compensated. Final. */
    DAMAGED;

    /** @return whether this state has no exit at all ({@code LOST}, {@code DAMAGED}) */
    public boolean isTerminal() {
        return ParcelStateMachine.TABLE.terminalStates().contains(this);
    }

    /** @return whether the only exit of this state is a dispute ({@code DELIVERED}, {@code RETURNED}) */
    public boolean isClosed() {
        Set<ParcelStatus> exits = ParcelStateMachine.TABLE.targetsFrom(this);
        return !exits.isEmpty() && exits.equals(Set.of(UNDER_DISPUTE));
    }

    public String getValue() {
        return name();
    }

    /**
     * Reads a status from its wire value, ignoring case.
     *
     * @throws IllegalArgumentException when the value names no status
     */
    public static ParcelStatus fromValue(String value) {
        for (ParcelStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown parcel status: " + value);
    }
}
