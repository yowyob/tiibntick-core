package com.yowyob.tiibntick.core.resource.domain.exception;

import java.util.UUID;

/**
 * Thrown when a vehicle cannot accommodate the requested load.
 * @author MANFOUO Braun.
 */
public class VehicleCapacityExceededException extends RuntimeException {

    public VehicleCapacityExceededException(UUID vehicleId, double requestedWeightKg,
            double maxWeightKg, double requestedVolumeM3, double maxVolumeM3) {
        super(String.format(
                "Vehicle %s capacity exceeded — requested: %.2f kg / %.3f m³, max: %.2f kg / %.3f m³",
                vehicleId, requestedWeightKg, requestedVolumeM3, maxWeightKg, maxVolumeM3));
    }
}
