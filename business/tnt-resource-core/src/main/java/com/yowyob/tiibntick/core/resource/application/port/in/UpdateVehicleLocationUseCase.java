package com.yowyob.tiibntick.core.resource.application.port.in;

import reactor.core.publisher.Mono;

/**
 * Inbound port: update the real-time GPS location of a vehicle.
 * Writes to Redis cache (fast path) and emits VehicleLocationUpdatedEvent.
 * @author MANFOUO Braun.
 */
public interface UpdateVehicleLocationUseCase {
    Mono<Void> updateLocation(UpdateVehicleLocationCommand command);
}
