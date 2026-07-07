package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Inbound port: retrieve a single vehicle by identifier.
 * @author MANFOUO Braun.
 */
public interface GetVehicleUseCase {
    Mono<Vehicle> getVehicle(UUID tenantId, UUID vehicleId);
}
