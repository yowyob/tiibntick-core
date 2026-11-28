package com.yowyob.tiibntick.core.delivery.domain.model.enums;

import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryStateMachine;

import java.util.EnumSet;
import java.util.Set;

/**
 * State of a delivery (spec {@code cible-evenements-colis-livraison.md} §1.1, decisions [C], [C bis]).
 *
 * <p>The transitions are <b>not</b> described here: the single table that holds them is
 * {@link DeliveryStateMachine#TABLE} (rows D1–D21). {@link #isTerminal()}, {@link #isActive()} and
 * {@link #isIncidentState()} are derived from that table.
 *
 * <p>{@code TIMED_OUT} and {@code SLA_BREACHED} were removed [C.3] (late deliveries are alerts, not states);
 * {@code AT_RELAY_POINT} was renamed {@link #AT_RELAY_HUB} (one name with the parcel, §2.3).
 */
public enum DeliveryStatus {

    /** Created, no courier responsible yet. */
    CREATED,
    /** A courier is designated; the parcel is not collected yet. */
    ASSIGNED,
    /** The courier collected the parcel (hand-over code). */
    PICKED_UP,
    /** The courier carries the parcel towards the next stop. */
    IN_TRANSIT,
    /** The parcel is deposited at a relay hub (intermediate stop or destination). */
    AT_RELAY_HUB,
    /** Last leg towards the recipient for one hand-over attempt. */
    OUT_FOR_DELIVERY,
    /** One hand-over attempt failed; another attempt is possible (attempt counter below the maximum). */
    DELIVERY_ATTEMPT_FAILED,
    /** Handed over to the recipient (or collected at the relay counter). Final. */
    DELIVERED,
    /** Finally failed (max attempts, refusal, not collected, incident without outcome, parcel lost/damaged). Final. */
    FAILED,
    /** Cancelled. Final. */
    CANCELLED,
    /** The courier declared an incident; transient state until triage (QO-1). */
    INCIDENT_REPORTED,
    /** A blocking incident stops the delivery until it is handled. */
    PAUSED_BY_INCIDENT,
    /** The parcel is being handed to a replacement courier. */
    INCIDENT_HANDOVER;

    /** States in which the courier physically holds the parcel (spec §1.1, derived {@code courierHoldsParcel}). */
    private static final Set<DeliveryStatus> COURIER_HOLDS =
            EnumSet.of(PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERY_ATTEMPT_FAILED);

    /** @return whether this state has no exit in {@link DeliveryStateMachine#TABLE} */
    public boolean isTerminal() {
        return DeliveryStateMachine.TABLE.terminalStates().contains(this);
    }

    /** @return a non-terminal state other than {@link #CREATED}: a mission engaged with a courier */
    public boolean isActive() {
        return !isTerminal() && this != CREATED;
    }

    /** @return a state left by restoring the memorised operational state (incident states) */
    public boolean isIncidentState() {
        return DeliveryStateMachine.TABLE.restoringStates().contains(this);
    }

    /**
     * @return whether the courier holds the parcel in this operational state (incident states excluded: use the
     *         memorised state for them)
     */
    public boolean courierHoldsParcel() {
        return COURIER_HOLDS.contains(this);
    }

    /** The wire value of this status (its name), as exposed to API clients. */
    public String getValue() {
        return name();
    }

    /**
     * Reads a status from its wire value, ignoring case. {@code AT_RELAY_POINT}, the name before lot evt-l2, is still
     * read as {@link #AT_RELAY_HUB} (offline payloads and clients written before the rename); it is never written.
     *
     * @throws IllegalArgumentException when the value names no status
     */
    public static DeliveryStatus fromValue(String value) {
        if ("AT_RELAY_POINT".equalsIgnoreCase(value)) {
            return AT_RELAY_HUB;
        }
        for (DeliveryStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown delivery status: " + value);
    }

    /** @return the names of the terminal states, for SQL filters built from the table */
    public static Set<DeliveryStatus> terminalStates() {
        return DeliveryStateMachine.TABLE.terminalStates();
    }
}
