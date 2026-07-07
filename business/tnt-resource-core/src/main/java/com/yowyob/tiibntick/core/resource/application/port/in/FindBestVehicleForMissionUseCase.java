package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import reactor.core.publisher.Mono;

/**
 * Inbound port: select the optimal vehicle for a mission given capacity constraints.
 * Used by tnt-delivery-core when assigning resources to missions.
 * @author MANFOUO Braun.
 */
public interface FindBestVehicleForMissionUseCase {
    Mono<Vehicle> findBestVehicle(FindBestVehicleCommand command);
}
