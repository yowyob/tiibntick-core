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
        Instant occurredAt,
        /**
         * Actor owning a personal vehicle (independent courier, no organisation), else {@code null}. Added
         * 2026-11-28 (decision D-184): an independent's vehicle has no {@code ownerOrgId}.
         */
        UUID ownerActorId
) {
    public static FreelancerVehicleRegisteredEvent of(UUID vehicleId, UUID ownerOrgId,
            VehicleType vehicleType, String plateNumber, double maxCapacityKg, FuelType fuelType) {
        return of(vehicleId, ownerOrgId, null, vehicleType, plateNumber, maxCapacityKg, fuelType);
    }

    public static FreelancerVehicleRegisteredEvent of(UUID vehicleId, UUID ownerOrgId, UUID ownerActorId,
            VehicleType vehicleType, String plateNumber, double maxCapacityKg, FuelType fuelType) {
        return new FreelancerVehicleRegisteredEvent(
                UUID.randomUUID(), vehicleId, ownerOrgId, vehicleType, plateNumber,
                maxCapacityKg, fuelType, Instant.now(), ownerActorId);
    }
}
