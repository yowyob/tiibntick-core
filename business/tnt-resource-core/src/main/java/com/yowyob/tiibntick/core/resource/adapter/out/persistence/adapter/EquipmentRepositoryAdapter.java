package com.yowyob.tiibntick.core.resource.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity.EquipmentEntity;
import com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository.EquipmentR2dbcRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.EquipmentRepository;
import com.yowyob.tiibntick.core.resource.domain.model.Equipment;
import com.yowyob.tiibntick.core.resource.domain.model.EquipmentStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Driven adapter: bridges the EquipmentRepository port to the R2DBC Spring Data repository.
 * @author MANFOUO Braun.
 */
@Component
public class EquipmentRepositoryAdapter implements EquipmentRepository {

    private final EquipmentR2dbcRepository r2dbcRepository;

    public EquipmentRepositoryAdapter(EquipmentR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    @Override
    public Mono<Equipment> save(Equipment equipment) {
        var _entity = EquipmentEntity.fromDomain(equipment);
        return r2dbcRepository.existsById(_entity.getId())
                .flatMap(exists -> {
                    _entity.setNew(!exists);
                    return r2dbcRepository.save(_entity);
                })
                .map(EquipmentEntity::toDomain);
    }

    @Override
    public Mono<Equipment> findById(UUID tenantId, UUID equipmentId) {
        return r2dbcRepository.findByTenantIdAndId(tenantId, equipmentId)
                .map(EquipmentEntity::toDomain);
    }

    @Override
    public Flux<Equipment> findByBranch(UUID tenantId, UUID branchId) {
        return r2dbcRepository.findByTenantIdAndBranchId(tenantId, branchId)
                .map(EquipmentEntity::toDomain);
    }

    @Override
    public Flux<Equipment> findByBranchAndStatus(UUID tenantId, UUID branchId, EquipmentStatus status) {
        return r2dbcRepository.findByTenantIdAndBranchIdAndStatus(tenantId, branchId, status.name())
                .map(EquipmentEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsBySerialNumber(UUID tenantId, String serialNumber) {
        return r2dbcRepository.existsByTenantIdAndSerialNumber(tenantId, serialNumber);
    }
}
