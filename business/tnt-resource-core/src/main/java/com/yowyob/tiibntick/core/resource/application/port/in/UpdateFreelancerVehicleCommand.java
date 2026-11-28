package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.FuelType;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleType;

import java.util.UUID;

/**
 * What the owner declares about an existing vehicle (organization or personal). The maximum capacity stays
 * mandatory ({@code > 0}).
 */
public record UpdateFreelancerVehicleCommand(
        UUID vehicleId,
        VehicleType type,
        String brand,
        String model,
        String plateNumber,
        double maxCapacityKg,
        Double volumeM3,
        FuelType fuelType,
        Double fuelConsumptionLPer100km
) {}
