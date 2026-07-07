package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import reactor.core.publisher.Mono;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Inbound port: mark a vehicle's maintenance as complete.
 * @author MANFOUO Braun.
 */
public interface CompleteVehicleMaintenanceUseCase {
    Mono<Vehicle> completeMaintenance(UUID tenantId, UUID vehicleId, LocalDate completionDate);
}
