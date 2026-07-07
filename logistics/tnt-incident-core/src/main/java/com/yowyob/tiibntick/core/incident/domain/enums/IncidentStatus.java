package com.yowyob.tiibntick.core.incident.domain.enums;

/**
 * Finite-state machine states for the Incident aggregate. Terminal states are CLOSED and CANCELLED.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */
public enum IncidentStatus {

    /** The incident has been automatically detected by the system (GPS anomaly, SLA breach, geofence trigger). */
    DETECTED,

    /** The incident has been manually reported by an actor. */
    REPORTED,

    /** The incident has been acknowledged and the associated mission has been paused. */
    ACKNOWLEDGED,

    /** Severity, geo-snapshot and risk score have been computed. */
    TRIAGED,

    /** The automated resolution engine is actively working on a solution. */
    AUTO_RESOLVING,

    /** The system is searching for and proposing a replacement driver. */
    REASSIGNING_DRIVER,

    /** A replacement driver has been assigned; waiting for dual-confirmation handover. */
    AWAITING_HANDOVER,

    /** The mission is being rerouted via tnt-route-core from the current position. */
    REROUTING,

    /** Parcels are being transferred to the nearest relay hub. */
    TRANSFERRING_TO_HUB,

    /** All automatic resolution attempts exhausted; awaiting agency intervention. */
    AUTO_RESOLUTION_FAILED,

    /** Waiting for an agency manager to take ownership of this incident. */
    PENDING_AGENCY_ASSIGNMENT,

    /** An agency manager is actively handling the incident. */
    AGENCY_HANDLING,

    /** An inter-agency cooperation request has been sent and is awaiting a response. */
    WAITING_INTERAGENCY,

    /** The inter-agency cooperation is currently underway. */
    INTERAGENCY_IN_PROGRESS,

    /** The incident has been escalated to a higher-level authority. */
    ESCALATED,

    /** Awaiting final confirmation before the incident can be closed. */
    PENDING_CONFIRMATION,

    /** The incident has been resolved. A close action is required to fully archive it. */
    RESOLVED,

    /**
     * Terminal state: the incident is fully closed and archived.
     * The blockchain chain is sealed and parcel chains are resumed.
     */
    CLOSED,

    /** Terminal state: the incident was cancelled and did not require resolution. */
    CANCELLED;

    /**
     * Returns {@code true} if this status represents a terminal (immutable) state.
     *
     * @return {@code true} for CLOSED and CANCELLED
     */
    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    /**
     * Returns {@code true} if the incident can still transition to RESOLVED.
     *
     * @return {@code false} for terminal and already-resolved states
     */
    public boolean isResolvable() {
        return !isTerminal() && this != RESOLVED;
    }

    /**
     * Returns {@code true} if the incident is currently in an automatic resolution sub-state.
     *
     * @return {@code true} for AUTO_RESOLVING, REASSIGNING_DRIVER, AWAITING_HANDOVER, REROUTING,
     *         and TRANSFERRING_TO_HUB
     */
    public boolean isAutoResolutionPhase() {
        return this == AUTO_RESOLVING || this == REASSIGNING_DRIVER
                || this == AWAITING_HANDOVER || this == REROUTING
                || this == TRANSFERRING_TO_HUB;
    }
}
