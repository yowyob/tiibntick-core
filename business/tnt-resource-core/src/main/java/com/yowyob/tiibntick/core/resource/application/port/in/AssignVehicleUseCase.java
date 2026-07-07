package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import reactor.core.publisher.Mono;

/**
 * Inbound port: assign a vehicle to a deliverer.
 * @author MANFOUO Braun.
 */
public interface AssignVehicleUseCase {
    Mono<Vehicle> assignVehicle(AssignVehicleCommand command);
}
