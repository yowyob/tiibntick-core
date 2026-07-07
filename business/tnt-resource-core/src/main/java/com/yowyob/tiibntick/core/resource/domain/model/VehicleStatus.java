package com.yowyob.tiibntick.core.resource.domain.model;

/**
 * Lifecycle states of a vehicle within TiiBnTick resource management.
 *
 * <p> — Added {@link #IN_INCIDENT_SUBSTITUTION} for tnt-incident-core integration.
 *
 * @author MANFOUO Braun
 */
public enum VehicleStatus {

    /** Vehicle is free and ready for assignment. */
    AVAILABLE,

    /** Vehicle is assigned to a deliverer for an active mission. */
    ASSIGNED,

    /** Vehicle is undergoing maintenance (scheduled or emergency). */
    IN_MAINTENANCE,

    /** Vehicle has been permanently removed from the fleet. */
    RETIRED,

    /**
     * Vehicle is temporarily lent to another agency for incident substitution.
     *
     * <p>Set by {@code IVehicleCompatibilityPort} / tnt-incident-core when an inter-agency
     * incident substitution is approved ({@code IncidentInterAgencyCooperation}).
     * The vehicle returns to {@link #AVAILABLE} or {@link #ASSIGNED} once the incident
     * is resolved and the substitution ends.
     *
     * <p>Transitions: {@code AVAILABLE} → {@code IN_INCIDENT_SUBSTITUTION} → {@code AVAILABLE}
     */
    IN_INCIDENT_SUBSTITUTION;

    /** Returns true if this vehicle can be assigned for a mission. */
    public boolean isAssignable() {
        return this == AVAILABLE;
    }

    /** Returns true if this vehicle is operationally blocked. */
    public boolean isBlocked() {
        return this == IN_MAINTENANCE || this == RETIRED || this == IN_INCIDENT_SUBSTITUTION;
    }
}
