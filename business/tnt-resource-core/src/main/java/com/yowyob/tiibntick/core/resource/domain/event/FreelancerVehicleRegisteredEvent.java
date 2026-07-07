package com.yowyob.tiibntick.core.resource.domain.event;

import com.yowyob.tiibntick.core.resource.domain.model.FuelType;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleType;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a new vehicle is registered in a FreelancerOrg fleet.
 *
 * <p>Published to Kafka topic: {@code tnt.resource.freelancer.vehicle.registered}
 * Consumed by: tnt-notify-core (KYC document reminder), tnt-billing-pricing (fleet profile update).
 *
 * @author MANFOUO Braun
 */
public record FreelancerVehicleRegisteredEvent(
        UUID eventId,
        UUID vehicleId,
        UUID ownerOrgId,
        VehicleType vehicleType,
        String plateNumber,
        double maxCapacityKg,
        FuelType fuelType,
        Instant occurredAt
) {
    public static FreelancerVehicleRegisteredEvent of(UUID vehicleId, UUID ownerOrgId,
            VehicleType vehicleType, String plateNumber, double maxCapacityKg, FuelType fuelType) {
        return new FreelancerVehicleRegisteredEvent(
                UUID.randomUUID(), vehicleId, ownerOrgId, vehicleType, plateNumber,
                maxCapacityKg, fuelType, Instant.now());
    }
}
