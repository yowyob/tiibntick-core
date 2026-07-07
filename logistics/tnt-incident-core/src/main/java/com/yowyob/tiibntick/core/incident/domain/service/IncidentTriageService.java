package com.yowyob.tiibntick.core.incident.domain.service;

import com.yowyob.tiibntick.core.incident.domain.enums.IncidentCategory;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentSeverity;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentType;

/**
 * Pure domain service deriving incident severity and category from the incident type.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public class IncidentTriageService {

    /**
     * Derives the incident severity from its specific type and category.
     *
     * @param type     the specific incident type
     * @param category the functional incident category
     * @return the computed {@link com.yowyob.tiibntick.core.incident.domain.enums.IncidentSeverity}
     */
    public IncidentSeverity determineSeverity(IncidentType type, IncidentCategory category) {
        return switch (type) {
            case HUMAN_SEVERE_INJURY, HUMAN_DRIVER_DECEASED, HUMAN_CLIENT_DECEASED,
                 HUMAN_HOSTAGE_SITUATION, HUMAN_ARMED_ATTACK, HUMAN_NATURAL_CATASTROPHE,
                 VEHICLE_FIRE, VEHICLE_COLLISION_MAJOR, VEHICLE_ROLLOVER, VEHICLE_IMMERSION
                    -> IncidentSeverity.FATAL;

            case DRIVER_DECEASED, REGULATORY_CARGO_SEIZURE, GEO_ARMED_CONFLICT,
                 GEO_TERRORIST_ZONE, SYSTEM_ACCOUNT_COMPROMISED, SYSTEM_API_COMPROMISED,
                 PARCEL_TOTAL_LOSS, SYSTEM_PROOF_FALSIFICATION
                    -> IncidentSeverity.CRITICAL;

            case DRIVER_MEDICAL_EMERGENCY, DRIVER_ACCIDENT_PHYSICAL, DRIVER_ARRESTED,
                 VEHICLE_COLLISION_MINOR, PARCEL_ILLEGAL_CONTENT, GEO_RIOT_CIVIL_UNREST,
                 DRIVER_COLLUSION_FRAUD, DRIVER_GPS_SPOOFING, DRIVER_PHANTOM_DELIVERY,
                 AGENCY_ENTIRE_FLEET_UNAVAILABLE, PARCEL_COLD_CHAIN_BROKEN
                    -> IncidentSeverity.HIGH;

            case DRIVER_VOLUNTARY_WITHDRAWAL_AFTER_PICKUP, VEHICLE_ENGINE_FAILURE,
                 VEHICLE_BRAKE_FAILURE, GEO_ROAD_FLOODED, GEO_ROAD_DESTROYED,
                 VEHICLE_COLD_CHAIN_FAILURE, PARCEL_PHYSICALLY_DAMAGED, PARCEL_PARTIAL_LOSS,
                 RELAY_POINT_CLOSED_UNEXPECTED, SYSTEM_KAFKA_FAILURE, SYSTEM_DATABASE_FAILURE
                    -> IncidentSeverity.MEDIUM;

            case DRIVER_VOLUNTARY_WITHDRAWAL_BEFORE_PICKUP, DRIVER_PHONE_DEAD,
                 DRIVER_NETWORK_LOSS, CLIENT_ABSENT_AT_DELIVERY, CLIENT_PHONE_UNREACHABLE,
                 VEHICLE_FUEL_SHORTAGE, VEHICLE_TIRE_PUNCTURE, RELAY_POINT_FULL_CAPACITY,
                 SLA_BREACH_TRAFFIC_DELAY, GO_EXCESSIVE_WAIT_TIME
                    -> IncidentSeverity.LOW;

            default -> deriveFromCategory(category);
        };
    }

    private IncidentSeverity deriveFromCategory(IncidentCategory category) {
        return switch (category) {
            case HUMAN_CRITICAL -> IncidentSeverity.CRITICAL;
            case VEHICLE, PARCEL_CARGO -> IncidentSeverity.HIGH;
            case DRIVER_DELIVERER, GEOGRAPHIC -> IncidentSeverity.MEDIUM;
            case CLIENT_RECIPIENT, RELAY_POINT, SLA_TIME -> IncidentSeverity.LOW;
            default -> IncidentSeverity.MEDIUM;
        };
    }

    /**
     * Infers the incident category from the incident type name prefix.
     *
     * @param type the specific incident type
     * @return the corresponding {@link com.yowyob.tiibntick.core.incident.domain.enums.IncidentCategory}
     */
    public IncidentCategory deriveCategory(IncidentType type) {
        String name = type.name();
        if (name.startsWith("DRIVER_")) return IncidentCategory.DRIVER_DELIVERER;
        if (name.startsWith("VEHICLE_")) return IncidentCategory.VEHICLE;
        if (name.startsWith("CLIENT_")) return IncidentCategory.CLIENT_RECIPIENT;
        if (name.startsWith("PARCEL_")) return IncidentCategory.PARCEL_CARGO;
        if (name.startsWith("SYSTEM_")) return IncidentCategory.SYSTEM_INFRASTRUCTURE;
        if (name.startsWith("GEO_")) return IncidentCategory.GEOGRAPHIC;
        if (name.startsWith("RELAY_")) return IncidentCategory.RELAY_POINT;
        if (name.startsWith("SLA_")) return IncidentCategory.SLA_TIME;
        if (name.startsWith("REGULATORY_")) return IncidentCategory.REGULATORY;
        if (name.startsWith("HUMAN_")) return IncidentCategory.HUMAN_CRITICAL;
        if (name.startsWith("AUTOMATION_")) return IncidentCategory.AUTOMATION_AI;
        if (name.startsWith("AGENCY_")) return IncidentCategory.INTER_AGENCY;
        return IncidentCategory.DRIVER_DELIVERER;
    }
}
