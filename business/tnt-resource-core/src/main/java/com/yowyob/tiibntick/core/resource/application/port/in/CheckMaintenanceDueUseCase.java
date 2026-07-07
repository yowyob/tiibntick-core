package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import reactor.core.publisher.Flux;
import java.util.UUID;

/**
 * Inbound port: retrieve all vehicles in a given agency that have maintenance overdue.
 * Consumed by the maintenance alert scheduler.
 * @author MANFOUO Braun.
 */
public interface CheckMaintenanceDueUseCase {
    Flux<Vehicle> findVehiclesWithMaintenanceDue(UUID tenantId, UUID agencyId);
}
