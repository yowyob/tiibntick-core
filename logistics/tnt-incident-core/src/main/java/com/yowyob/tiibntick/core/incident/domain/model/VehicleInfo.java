package com.yowyob.tiibntick.core.incident.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Immutable vehicle information for incident auto-resolution.
 *
 * <p>This record is used by {@code tnt-incident-core} to query vehicle
 * characteristics from {@code tnt-resource-core} without exposing internal
 * domain types across modules.
 *
 * <p>Key fields:
 * <ul>
 *   <li>{@code vehicleId} — the vehicle UUID</li>
 *   <li>{@code agencyId} — owning agency UUID</li>
 *   <li>{@code category} — vehicle type name (e.g., "VAN", "TRUCK")</li>
 *   <li>{@code maxCapacityKg} — maximum load in kilograms</li>
 *   <li>{@code hasRefrigeration} — cold chain capability flag</li>
 *   <li>{@code status} — current vehicle status name</li>
 * </ul>
 *
 * @author MANFOUO Braun
 * @see com.yowyob.tiibntick.core.incident.port.outbound.IVehicleCompatibilityPort
 */
@Value
@Builder
public class VehicleInfo {

    UUID vehicleId;
    UUID agencyId;
    String category;
    double maxCapacityKg;
    double volumeM3;
    boolean hasRefrigeration;
    String status;

    /**
     * Creates a "not found" vehicle info record.
     *
     * @param vehicleId the requested vehicle UUID
     * @return a {@code VehicleInfo} instance representing a non-existent vehicle
     */
    public static VehicleInfo notFound(UUID vehicleId) {
        return VehicleInfo.builder()
                .vehicleId(vehicleId)
                .category("NOT_FOUND")
                .maxCapacityKg(0)
                .volumeM3(0)
                .hasRefrigeration(false)
                .status("NOT_FOUND")
                .build();
    }
}
