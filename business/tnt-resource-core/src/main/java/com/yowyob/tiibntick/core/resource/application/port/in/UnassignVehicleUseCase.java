package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Inbound port: release a vehicle from its current assignment.
 * @author MANFOUO Braun.
 */
public interface UnassignVehicleUseCase {
    Mono<Vehicle> unassignVehicle(UUID tenantId, UUID vehicleId);
}
