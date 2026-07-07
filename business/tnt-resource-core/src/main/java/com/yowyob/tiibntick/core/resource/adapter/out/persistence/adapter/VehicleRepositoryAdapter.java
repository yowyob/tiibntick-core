package com.yowyob.tiibntick.core.resource.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity.VehicleEntity;
import com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository.VehicleR2dbcRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.VehicleRepository;
import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Driven adapter: bridges the VehicleRepository port to the R2DBC Spring Data repository.
 * @author MANFOUO Braun.
 */
@Component
public class VehicleRepositoryAdapter implements VehicleRepository {

    private final VehicleR2dbcRepository r2dbcRepository;

    public VehicleRepositoryAdapter(VehicleR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    @Override
    public Mono<Vehicle> save(Vehicle vehicle) {
        var _entity = VehicleEntity.fromDomain(vehicle);
        return r2dbcRepository.existsById(_entity.getId())
                .flatMap(exists -> {
                    _entity.setNew(!exists);
                    return r2dbcRepository.save(_entity);
                })
                .map(VehicleEntity::toDomain);
    }

    @Override
    public Mono<Vehicle> findById(UUID tenantId, UUID vehicleId) {
        return r2dbcRepository.findByTenantIdAndId(tenantId, vehicleId)
                .map(VehicleEntity::toDomain);
    }

    @Override
    public Flux<Vehicle> findByAgency(UUID tenantId, UUID agencyId) {
        return r2dbcRepository.findByTenantIdAndAgencyId(tenantId, agencyId)
                .map(VehicleEntity::toDomain);
    }

    @Override
    public Flux<Vehicle> findByAgencyAndStatus(UUID tenantId, UUID agencyId, VehicleStatus status) {
        return r2dbcRepository.findByTenantIdAndAgencyIdAndStatus(tenantId, agencyId, status.name())
                .map(VehicleEntity::toDomain);
    }

    @Override
    public Flux<Vehicle> findAvailableByAgency(UUID tenantId, UUID agencyId) {
        return r2dbcRepository.findAvailableByTenantIdAndAgencyId(tenantId, agencyId)
                .map(VehicleEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsByRegistrationNumber(UUID tenantId, String registrationNumber) {
        return r2dbcRepository.existsByTenantIdAndRegistrationNumber(tenantId, registrationNumber);
    }

    @Override
    public Mono<Long> countByAgencyAndStatus(UUID tenantId, UUID agencyId, VehicleStatus status) {
        return r2dbcRepository.countByTenantIdAndAgencyIdAndStatus(tenantId, agencyId, status.name());
    }

    @Override
    public Mono<Vehicle> findByIdNoTenant(UUID vehicleId) {
        return r2dbcRepository.findById(vehicleId).map(VehicleEntity::toDomain);
    }

    @Override
    public Flux<Vehicle> findAvailableNear(UUID tenantId, UUID agencyId,
                                           double latitude, double longitude,
                                           double requiredCapacityKg, String vehicleCategory) {
        return r2dbcRepository.findAvailableNear(tenantId, agencyId, latitude, longitude,
                        requiredCapacityKg, vehicleCategory)
                .map(VehicleEntity::toDomain);
    }
}

