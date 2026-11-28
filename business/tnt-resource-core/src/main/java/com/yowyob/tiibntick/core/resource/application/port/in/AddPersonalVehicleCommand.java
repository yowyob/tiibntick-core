package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.FuelType;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleType;

import java.util.UUID;

/**
 * Declares the personal vehicle of an independent courier (no organization, lot L3). The maximum capacity is
 * mandatory ({@code > 0}) as for any vehicle.
 *
 * @param vehicleId optional id to keep (e.g. an id already referenced by a calling module), {@code null} otherwise
 */
public record AddPersonalVehicleCommand(
        UUID vehicleId,
        UUID ownerActorId,
        VehicleType type,
        String brand,
        String model,
        String plateNumber,
        double maxCapacityKg,
        Double volumeM3,
        FuelType fuelType,
        Double fuelConsumptionLPer100km,
        String registrationDocRef,
        String insuranceDocRef
) {}
