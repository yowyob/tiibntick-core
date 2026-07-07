package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Inbound port: permanently retire a vehicle from the fleet.
 * @author MANFOUO Braun.
 */
public interface RetireVehicleUseCase {
    Mono<Vehicle> retireVehicle(UUID tenantId, UUID vehicleId);
}
