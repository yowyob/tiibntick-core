package com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity.FreelancerVehicleEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for FreelancerVehicleEntity.
 * @author MANFOUO Braun
 */
public interface FreelancerVehicleR2dbcRepository
        extends ReactiveCrudRepository<FreelancerVehicleEntity, UUID> {

    @Query("SELECT * FROM tnt_freelancer_vehicles WHERE freelancer_org_id = :orgId ORDER BY created_at ASC")
    Flux<FreelancerVehicleEntity> findByFreelancerOrgId(UUID orgId);

    @Query("SELECT * FROM tnt_freelancer_vehicles WHERE freelancer_org_id = :orgId AND is_active = true AND current_mission_id IS NULL ORDER BY created_at ASC")
    Flux<FreelancerVehicleEntity> findAvailableByFreelancerOrgId(UUID orgId);

    @Query("SELECT COUNT(*) FROM tnt_freelancer_vehicles WHERE freelancer_org_id = :orgId")
    Mono<Long> countByFreelancerOrgId(UUID orgId);

    @Query("SELECT EXISTS(SELECT 1 FROM tnt_freelancer_vehicles WHERE freelancer_org_id = :orgId AND plate_number = :plateNumber)")
    Mono<Boolean> existsByFreelancerOrgIdAndPlateNumber(UUID orgId, String plateNumber);
}
