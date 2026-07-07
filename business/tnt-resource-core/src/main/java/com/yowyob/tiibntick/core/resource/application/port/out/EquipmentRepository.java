package com.yowyob.tiibntick.core.resource.application.port.out;

import com.yowyob.tiibntick.core.resource.domain.model.Equipment;
import com.yowyob.tiibntick.core.resource.domain.model.EquipmentStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port: persistence contract for Equipment entity.
 * @author MANFOUO Braun.
 */
public interface EquipmentRepository {

    Mono<Equipment> save(Equipment equipment);

    Mono<Equipment> findById(UUID tenantId, UUID equipmentId);

    Flux<Equipment> findByBranch(UUID tenantId, UUID branchId);

    Flux<Equipment> findByBranchAndStatus(UUID tenantId, UUID branchId, EquipmentStatus status);

    Mono<Boolean> existsBySerialNumber(UUID tenantId, String serialNumber);
}
