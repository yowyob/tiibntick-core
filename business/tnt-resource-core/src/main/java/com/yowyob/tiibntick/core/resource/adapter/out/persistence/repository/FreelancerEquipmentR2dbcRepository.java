package com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity.FreelancerEquipmentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for FreelancerEquipmentEntity.
 * @author MANFOUO Braun
 */
public interface FreelancerEquipmentR2dbcRepository
        extends ReactiveCrudRepository<FreelancerEquipmentEntity, UUID> {

    @Query("SELECT * FROM tnt_freelancer_equipments WHERE freelancer_org_id = :orgId ORDER BY created_at ASC")
    Flux<FreelancerEquipmentEntity> findByFreelancerOrgId(UUID orgId);

    @Query("SELECT * FROM tnt_freelancer_equipments WHERE freelancer_org_id = :orgId AND is_active = true ORDER BY created_at ASC")
    Flux<FreelancerEquipmentEntity> findActiveByFreelancerOrgId(UUID orgId);

    @Query("SELECT EXISTS(SELECT 1 FROM tnt_freelancer_equipments WHERE freelancer_org_id = :orgId AND equipment_type = :type AND is_active = true)")
    Mono<Boolean> existsActiveByFreelancerOrgIdAndType(UUID orgId, String type);
}
