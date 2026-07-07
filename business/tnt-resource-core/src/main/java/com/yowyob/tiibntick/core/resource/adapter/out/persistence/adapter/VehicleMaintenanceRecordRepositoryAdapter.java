package com.yowyob.tiibntick.core.resource.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity.VehicleMaintenanceRecordEntity;
import com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository.VehicleMaintenanceRecordR2dbcRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.VehicleMaintenanceRecordRepository;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleMaintenanceRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Driven adapter: bridges the VehicleMaintenanceRecordRepository port to R2DBC.
 * @author MANFOUO Braun.
 */
@Component
public class VehicleMaintenanceRecordRepositoryAdapter implements VehicleMaintenanceRecordRepository {

    private final VehicleMaintenanceRecordR2dbcRepository r2dbcRepository;

    public VehicleMaintenanceRecordRepositoryAdapter(VehicleMaintenanceRecordR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    @Override
    public Mono<VehicleMaintenanceRecord> save(VehicleMaintenanceRecord record) {
        var _entity = VehicleMaintenanceRecordEntity.fromDomain(record);
        return r2dbcRepository.existsById(_entity.getId())
                .flatMap(exists -> {
                    _entity.setNew(!exists);
                    return r2dbcRepository.save(_entity);
                })
                .map(VehicleMaintenanceRecordEntity::toDomain);
    }

    @Override
    public Flux<VehicleMaintenanceRecord> findByVehicleId(UUID tenantId, UUID vehicleId) {
        return r2dbcRepository.findByTenantIdAndVehicleId(tenantId, vehicleId)
                .map(VehicleMaintenanceRecordEntity::toDomain);
    }

    @Override
    public Mono<VehicleMaintenanceRecord> findById(UUID recordId) {
        return r2dbcRepository.findById(recordId)
                .map(VehicleMaintenanceRecordEntity::toDomain);
    }
}
