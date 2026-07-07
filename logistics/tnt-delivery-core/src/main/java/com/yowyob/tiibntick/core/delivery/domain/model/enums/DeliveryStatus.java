package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Represents every possible state in the delivery lifecycle.
 *
 * <p>State machine:
 * <pre>
 *   CREATED ──(pickup confirmed)──► PICKED_UP
 *   PICKED_UP ──(driver departs)──► IN_TRANSIT
 *   IN_TRANSIT ──(delivered)──────► DELIVERED
 *   IN_TRANSIT ──(incident)───────► PAUSED_BY_INCIDENT
 *   PAUSED_BY_INCIDENT ──(resolved)► IN_TRANSIT | PICKED_UP (depending on what was paused)
 *   IN_TRANSIT ──(failure)────────► FAILED
 *   CREATED | PICKED_UP ──────────► CANCELLED
 *   IN_TRANSIT ──(hub stop)───────► AT_RELAY_POINT
 *   AT_RELAY_POINT ──(resumed)────► IN_TRANSIT
 *   FAILED ──(retry)──────────────► IN_TRANSIT
 * </pre>
 *
 * <p> — Added statuses for tnt-incident-core integration:
 * <ul>
 *   <li>{@link #PAUSED_BY_INCIDENT} — delivery is blocked by an ongoing incident.
 *       Set by {@code IMissionStatusPort.pauseMission()}. The previous status is
 *       saved in {@code Delivery.previousStatusBeforePause} for restoration on resolution.</li>
 *   <li>{@link #TIMED_OUT} — delivery exceeded its scheduled SLA window.
 *       Published in {@code MissionStatusChangedEvent} to trigger auto-incident creation
 *       in {@code tnt-incident-core}.</li>
 *   <li>{@link #SLA_BREACHED} — soft SLA warning before full timeout.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum DeliveryStatus {

    /** Delivery request created and waiting for driver assignment or pickup. */
    CREATED,

    /** Package has been physically collected by the delivery person. */
    PICKED_UP,

    /** Package is currently being transported to the destination. */
    IN_TRANSIT,

    /** Package is temporarily stored at a relay / hub point. */
    AT_RELAY_POINT,

    /** Package has been successfully handed to the recipient. */
    DELIVERED,

    /** Delivery attempt failed (incident, refusal, address not found, etc.). */
    FAILED,

    /** Delivery cancelled before package collection. */
    CANCELLED,

    /**
     * Delivery is temporarily blocked by an active incident managed by tnt-incident-core.
     *
     * <p>Set by {@code MissionStatusPortAdapter.pauseMission(missionId, incidentId)}.
     * The delivery's {@code pausedByIncidentId} field holds the blocking incident UUID,
     * and {@code previousStatusBeforePause} holds the status to restore upon resolution.
     *
     * <p>Transitions out: → IN_TRANSIT | PICKED_UP (via {@code MissionStatusPortAdapter.resumeMission()})
     * when the incident is resolved or closed.
     */
    PAUSED_BY_INCIDENT,

    /**
     * Delivery has exceeded its scheduled delivery time window (SLA timeout).
     *
     * <p>This status is set by the SLA monitoring scheduler and published in a
     * {@code MissionStatusChangedEvent} with status {@code TIMED_OUT} to
     * trigger automatic incident creation in tnt-incident-core.
     */
    TIMED_OUT,

    /**
     * Delivery is approaching its SLA deadline (soft warning).
     *
     * <p>Published as a {@code MissionStatusChangedEvent} to allow tnt-incident-core
     * to pre-emptively escalate if no progress is made.
     */
    SLA_BREACHED;

    /**
     * Returns {@code true} when this status is a terminal state
     * (no further transitions are expected).
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == FAILED;
    }

    /**
     * Returns {@code true} when a delivery in this status is still active
     * (ongoing logistics operation).
     */
    public boolean isActive() {
        return this == PICKED_UP || this == IN_TRANSIT || this == AT_RELAY_POINT;
    }

    /**
     * Returns {@code true} if this delivery is currently paused due to an incident.
     */
    public boolean isPausedByIncident() {
        return this == PAUSED_BY_INCIDENT;
    }
}
