package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import reactor.core.publisher.Mono;

/**
 * Inbound port: register a new vehicle in the fleet.
 * @author MANFOUO Braun.
 */
public interface CreateVehicleUseCase {
    Mono<Vehicle> createVehicle(CreateVehicleCommand command);
}
