package com.yowyob.tiibntick.core.incident.domain.service;

import com.yowyob.tiibntick.core.incident.domain.enums.IncidentSeverity;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Whether an incident blocks its delivery (provisional decision QO-2): a type/severity rule, OR the declaring courier
 * forces it ("I cannot go on"). Blocking → the delivery is paused (row D15); otherwise it goes on (row D14).
 */
public class IncidentBlockingPolicy {

    /** Severities that always block. */
    static final Set<IncidentSeverity> BLOCKING_SEVERITIES =
            EnumSet.of(IncidentSeverity.HIGH, IncidentSeverity.CRITICAL, IncidentSeverity.FATAL);

    /** Types that block whatever their severity: the courier, the vehicle or the parcel cannot go on. */
    static final Set<IncidentType> BLOCKING_TYPES = EnumSet.of(
            IncidentType.DRIVER_VOLUNTARY_WITHDRAWAL_BEFORE_PICKUP,
            IncidentType.DRIVER_VOLUNTARY_WITHDRAWAL_AFTER_PICKUP,
            IncidentType.DRIVER_SUDDEN_UNAVAILABILITY,
            IncidentType.DRIVER_MEDICAL_EMERGENCY,
            IncidentType.DRIVER_ACCIDENT_PHYSICAL,
            IncidentType.DRIVER_ARRESTED,
            IncidentType.DRIVER_PHONE_DEAD,
            IncidentType.VEHICLE_ENGINE_FAILURE,
            IncidentType.VEHICLE_BRAKE_FAILURE,
            IncidentType.VEHICLE_TRANSMISSION_FAILURE,
            IncidentType.VEHICLE_ELECTRICAL_FAILURE,
            IncidentType.VEHICLE_COLLISION_MAJOR,
            IncidentType.VEHICLE_ROLLOVER,
            IncidentType.VEHICLE_FIRE,
            IncidentType.VEHICLE_IMMERSION);

    /**
     * @param type             declared type
     * @param severity         severity computed at triage
     * @param forcedByCourier  the courier ticked "I cannot go on"
     * @return whether the incident blocks the delivery
     */
    public boolean isBlocking(IncidentType type, IncidentSeverity severity, boolean forcedByCourier) {
        return forcedByCourier
                || (severity != null && BLOCKING_SEVERITIES.contains(severity))
                || (type != null && BLOCKING_TYPES.contains(type));
    }
}
