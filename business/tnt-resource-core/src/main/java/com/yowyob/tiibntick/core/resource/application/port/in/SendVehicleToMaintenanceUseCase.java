package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import reactor.core.publisher.Mono;

/**
 * Inbound port: send a vehicle to maintenance.
 * @author MANFOUO Braun.
 */
public interface SendVehicleToMaintenanceUseCase {
    Mono<Vehicle> sendToMaintenance(SendVehicleToMaintenanceCommand command);
}
