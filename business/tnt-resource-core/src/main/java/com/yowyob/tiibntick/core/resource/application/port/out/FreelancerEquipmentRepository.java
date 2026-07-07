package com.yowyob.tiibntick.core.resource.application.port.out;

import com.yowyob.tiibntick.core.resource.domain.model.EquipmentType;
import com.yowyob.tiibntick.core.resource.domain.model.FreelancerEquipment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port: persistence contract for {@link FreelancerEquipment}.
 *
 * @author MANFOUO Braun
 */
public interface FreelancerEquipmentRepository {

    Mono<FreelancerEquipment> save(FreelancerEquipment equipment);

    Mono<FreelancerEquipment> findById(UUID equipmentId);

    Flux<FreelancerEquipment> findByOwnerOrgId(UUID ownerOrgId);

    Flux<FreelancerEquipment> findActiveByOwnerOrgId(UUID ownerOrgId);

    Mono<Boolean> existsActiveByOwnerOrgIdAndType(UUID ownerOrgId, EquipmentType type);

    Mono<Void> deleteById(UUID equipmentId);
}
