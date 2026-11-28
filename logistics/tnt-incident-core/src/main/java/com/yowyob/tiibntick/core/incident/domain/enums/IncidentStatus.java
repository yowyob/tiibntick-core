package com.yowyob.tiibntick.core.incident.domain.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

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

    /**
     * Declared by the assigned courier (lot L3: {@code DETECTED} is removed, detections are delivery alerts [D.1]).
     */
    REPORTED,

    /** The incident has been acknowledged. */
    ACKNOWLEDGED,

    /** Severity, geo-snapshot, risk score and {@code blocking} have been computed. */
    TRIAGED,

    /** The automated resolution engine is actively working on a solution. */
    AUTO_RESOLVING,

    /** The system is searching for and proposing a replacement driver. */
    REASSIGNING_DRIVER,

    /** A replacement driver has been assigned; waiting for dual-confirmation handover. */
    AWAITING_HANDOVER,

    /** The mission is being rerouted via tnt-route-core from the current position. */
    REROUTING,

    /** Parcels are being transferred to the nearest relay hub (QO-13: no replacement, a relay in range). */
    TRANSFERRING_TO_HUB,

    /** All automatic resolution attempts exhausted; awaiting the manager. */
    AUTO_RESOLUTION_FAILED,

    /** Waiting for the manager (the delivery's agency, else the TiiBnTick support) to take ownership. */
    PENDING_AGENCY_ASSIGNMENT,

    /** The manager is actively handling the incident. */
    AGENCY_HANDLING,

    /** An inter-agency cooperation request has been sent and is awaiting a response. */
    WAITING_INTERAGENCY,

    /** The inter-agency cooperation is currently underway. */
    INTERAGENCY_IN_PROGRESS,

    /** The incident has been escalated to a higher-level authority. */
    ESCALATED,

    /** The manager proposed a resolution; the declaring courier confirms or contests it (QO-14). */
    PENDING_CONFIRMATION,

    /** The incident has been resolved. A close action is required to fully archive it. */
    RESOLVED,

    /** Terminal state: the incident is fully closed and archived. */
    CLOSED,

    /** Terminal state: the incident was cancelled (erroneous declaration). */
    CANCELLED;

    /**
     * Transition table of spec §1.3 (rows I1–I31), the single source of truth: a move absent from it is refused by
     * {@link com.yowyob.tiibntick.core.incident.domain.model.Incident}.
     */
    private static final Map<IncidentStatus, Set<IncidentStatus>> TRANSITIONS = buildTransitions();

    private static Map<IncidentStatus, Set<IncidentStatus>> buildTransitions() {
        Map<IncidentStatus, Set<IncidentStatus>> t = new EnumMap<>(IncidentStatus.class);
        t.put(REPORTED, EnumSet.of(ACKNOWLEDGED));                                                    // I2
        t.put(ACKNOWLEDGED, EnumSet.of(TRIAGED));                                                     // I3
        t.put(TRIAGED, EnumSet.of(AUTO_RESOLVING, PENDING_AGENCY_ASSIGNMENT, RESOLVED));              // I4 I5 I6
        t.put(AUTO_RESOLVING, EnumSet.of(REASSIGNING_DRIVER, REROUTING, TRANSFERRING_TO_HUB,          // I7 I8 I9
                AUTO_RESOLUTION_FAILED));                                                             // I10
        t.put(REASSIGNING_DRIVER, EnumSet.of(AWAITING_HANDOVER, RESOLVED, AUTO_RESOLUTION_FAILED));   // I12 I16 I10
        t.put(AWAITING_HANDOVER, EnumSet.of(RESOLVED, REASSIGNING_DRIVER));                           // I14 I15
        t.put(REROUTING, EnumSet.of(RESOLVED, AUTO_RESOLUTION_FAILED));                               // I17 I10
        t.put(TRANSFERRING_TO_HUB, EnumSet.of(RESOLVED, AUTO_RESOLUTION_FAILED));                     // I17 I10
        t.put(AUTO_RESOLUTION_FAILED, EnumSet.of(PENDING_AGENCY_ASSIGNMENT));                         // I11
        t.put(PENDING_AGENCY_ASSIGNMENT, EnumSet.of(AGENCY_HANDLING));                                // I18
        t.put(AGENCY_HANDLING, EnumSet.of(WAITING_INTERAGENCY, AWAITING_HANDOVER,                     // I19 I13
                PENDING_CONFIRMATION, RESOLVED));                                                     // I23 I26
        t.put(WAITING_INTERAGENCY, EnumSet.of(INTERAGENCY_IN_PROGRESS, AGENCY_HANDLING));             // I20 I21
        t.put(INTERAGENCY_IN_PROGRESS, EnumSet.of(AGENCY_HANDLING, RESOLVED));                        // I21 I22
        t.put(ESCALATED, EnumSet.of(AGENCY_HANDLING, PENDING_CONFIRMATION, RESOLVED));                // I28 I23 I29
        t.put(PENDING_CONFIRMATION, EnumSet.of(RESOLVED, AGENCY_HANDLING));                           // I24 I25
        t.put(RESOLVED, EnumSet.of(CLOSED));                                                          // I30
        t.put(CLOSED, EnumSet.noneOf(IncidentStatus.class));
        t.put(CANCELLED, EnumSet.noneOf(IncidentStatus.class));
        for (IncidentStatus s : values()) {
            if (s.isEscalatable()) {
                t.get(s).add(ESCALATED);                                                              // I27
            }
            if (!s.isTerminal() && s != RESOLVED) {
                t.get(s).add(CANCELLED);                                                              // I31
            }
        }
        Map<IncidentStatus, Set<IncidentStatus>> frozen = new EnumMap<>(IncidentStatus.class);
        t.forEach((k, v) -> frozen.put(k, Collections.unmodifiableSet(v)));
        return Collections.unmodifiableMap(frozen);
    }

    /** @return whether row I1–I31 allows {@code this → target} */
    public boolean canTransitionTo(IncidentStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** @return the states reachable from this one (spec §1.3) */
    public Set<IncidentStatus> allowedTargets() {
        return TRANSITIONS.get(this);
    }

    /** Row I27: the states from which an incident can be escalated (ESCALATED included: level + 1). */
    public boolean isEscalatable() {
        return !isTerminal() && this != REPORTED && this != RESOLVED && this != PENDING_CONFIRMATION;
    }

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
