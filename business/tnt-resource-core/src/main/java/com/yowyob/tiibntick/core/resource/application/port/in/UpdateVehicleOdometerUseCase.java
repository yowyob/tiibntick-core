package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import reactor.core.publisher.Mono;

/**
 * Inbound port: update a vehicle's odometer reading.
 * @author MANFOUO Braun.
 */
public interface UpdateVehicleOdometerUseCase {
    Mono<Vehicle> updateOdometer(UpdateVehicleOdometerCommand command);
}
