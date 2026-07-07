package com.yowyob.tiibntick.core.resource.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity.FreelancerEquipmentEntity;
import com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository.FreelancerEquipmentR2dbcRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.FreelancerEquipmentRepository;
import com.yowyob.tiibntick.core.resource.domain.model.EquipmentType;
import com.yowyob.tiibntick.core.resource.domain.model.FreelancerEquipment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Driven adapter: bridges FreelancerEquipmentRepository port to R2DBC Spring Data.
 * @author MANFOUO Braun
 */
@Component
public class FreelancerEquipmentRepositoryAdapter implements FreelancerEquipmentRepository {

    private final FreelancerEquipmentR2dbcRepository r2dbcRepository;

    public FreelancerEquipmentRepositoryAdapter(FreelancerEquipmentR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    @Override
    public Mono<FreelancerEquipment> save(FreelancerEquipment equipment) {
        var _entity = FreelancerEquipmentEntity.fromDomain(equipment);
        return r2dbcRepository.existsById(_entity.getId())
                .flatMap(exists -> {
                    _entity.setNew(!exists);
                    return r2dbcRepository.save(_entity);
                })
                .map(FreelancerEquipmentEntity::toDomain);
    }

    @Override
    public Mono<FreelancerEquipment> findById(UUID equipmentId) {
        return r2dbcRepository.findById(equipmentId)
                .map(FreelancerEquipmentEntity::toDomain);
    }

    @Override
    public Flux<FreelancerEquipment> findByOwnerOrgId(UUID ownerOrgId) {
        return r2dbcRepository.findByFreelancerOrgId(ownerOrgId)
                .map(FreelancerEquipmentEntity::toDomain);
    }

    @Override
    public Flux<FreelancerEquipment> findActiveByOwnerOrgId(UUID ownerOrgId) {
        return r2dbcRepository.findActiveByFreelancerOrgId(ownerOrgId)
                .map(FreelancerEquipmentEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsActiveByOwnerOrgIdAndType(UUID ownerOrgId, EquipmentType type) {
        return r2dbcRepository.existsActiveByFreelancerOrgIdAndType(ownerOrgId, type.name());
    }

    @Override
    public Mono<Void> deleteById(UUID equipmentId) {
        return r2dbcRepository.deleteById(equipmentId);
    }
}
