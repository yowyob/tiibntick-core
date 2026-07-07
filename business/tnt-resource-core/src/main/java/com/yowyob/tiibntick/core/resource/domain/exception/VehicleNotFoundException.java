package com.yowyob.tiibntick.core.resource.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

/**
 * Thrown when a requested vehicle does not exist in the TiiBnTick fleet.
 * @author MANFOUO Braun.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class VehicleNotFoundException extends RuntimeException {

    private final UUID vehicleId;

    public VehicleNotFoundException(UUID vehicleId) {
        super("Vehicle not found: " + vehicleId);
        this.vehicleId = vehicleId;
    }

    public UUID getVehicleId() { return vehicleId; }
}
