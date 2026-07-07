package com.yowyob.tiibntick.core.resource.application.port.out;

import com.yowyob.tiibntick.core.resource.domain.model.VehicleMaintenanceRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port: persistence contract for VehicleMaintenanceRecord entity.
 * @author MANFOUO Braun.
 */
public interface VehicleMaintenanceRecordRepository {

    Mono<VehicleMaintenanceRecord> save(VehicleMaintenanceRecord record);

    Flux<VehicleMaintenanceRecord> findByVehicleId(UUID tenantId, UUID vehicleId);

    Mono<VehicleMaintenanceRecord> findById(UUID recordId);
}
