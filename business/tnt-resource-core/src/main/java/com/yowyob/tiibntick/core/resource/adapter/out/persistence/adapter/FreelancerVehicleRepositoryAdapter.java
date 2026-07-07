package com.yowyob.tiibntick.core.resource.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity.FreelancerVehicleEntity;
import com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository.FreelancerVehicleR2dbcRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Driven adapter: bridges FreelancerVehicleRepository port to R2DBC Spring Data.
 * @author MANFOUO Braun
 */
@Component
public class FreelancerVehicleRepositoryAdapter implements FreelancerVehicleRepository {

    private final FreelancerVehicleR2dbcRepository r2dbcRepository;

    public FreelancerVehicleRepositoryAdapter(FreelancerVehicleR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    @Override
    public Mono<FreelancerVehicle> save(FreelancerVehicle vehicle) {
        var _entity = FreelancerVehicleEntity.fromDomain(vehicle);
        return r2dbcRepository.existsById(_entity.getId())
                .flatMap(exists -> {
                    _entity.setNew(!exists);
                    return r2dbcRepository.save(_entity);
                })
                .map(FreelancerVehicleEntity::toDomain);
    }

    @Override
    public Mono<FreelancerVehicle> findById(UUID vehicleId) {
        return r2dbcRepository.findById(vehicleId)
                .map(FreelancerVehicleEntity::toDomain);
    }

    @Override
    public Flux<FreelancerVehicle> findByOwnerOrgId(UUID ownerOrgId) {
        return r2dbcRepository.findByFreelancerOrgId(ownerOrgId)
                .map(FreelancerVehicleEntity::toDomain);
    }

    @Override
    public Flux<FreelancerVehicle> findAvailableByOwnerOrgId(UUID ownerOrgId) {
        return r2dbcRepository.findAvailableByFreelancerOrgId(ownerOrgId)
                .map(FreelancerVehicleEntity::toDomain);
    }

    @Override
    public Mono<Long> countByOwnerOrgId(UUID ownerOrgId) {
        return r2dbcRepository.countByFreelancerOrgId(ownerOrgId);
    }

    @Override
    public Mono<Boolean> existsByOwnerOrgIdAndPlateNumber(UUID ownerOrgId, String plateNumber) {
        return r2dbcRepository.existsByFreelancerOrgIdAndPlateNumber(ownerOrgId, plateNumber);
    }

    @Override
    public Mono<Void> deleteById(UUID vehicleId) {
        return r2dbcRepository.deleteById(vehicleId);
    }
}
