package com.yowyob.tiibntick.core.resource.domain.exception;

import com.yowyob.tiibntick.core.resource.domain.model.VehicleStatus;

import java.util.UUID;

/**
 * Thrown when an illegal status transition is attempted on a vehicle.
 * @author MANFOUO Braun.
 */
public class VehicleStatusTransitionException extends RuntimeException {

    private final UUID vehicleId;
    private final VehicleStatus currentStatus;
    private final VehicleStatus targetStatus;

    public VehicleStatusTransitionException(UUID vehicleId, VehicleStatus current, VehicleStatus target) {
        super(String.format("Vehicle %s cannot transition from %s to %s", vehicleId, current, target));
        this.vehicleId = vehicleId;
        this.currentStatus = current;
        this.targetStatus = target;
    }

    public UUID getVehicleId() { return vehicleId; }
    public VehicleStatus getCurrentStatus() { return currentStatus; }
    public VehicleStatus getTargetStatus() { return targetStatus; }
}
