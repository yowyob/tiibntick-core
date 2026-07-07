package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleStatus;
import reactor.core.publisher.Flux;
import java.util.UUID;

/**
 * Inbound port: list all vehicles belonging to a given agency, optionally filtered by status.
 * @author MANFOUO Braun.
 */
public interface ListVehiclesByAgencyUseCase {
    Flux<Vehicle> listByAgency(UUID tenantId, UUID agencyId, VehicleStatus statusFilter);
}
