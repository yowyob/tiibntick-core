package com.yowyob.tiibntick.core.agency.assignment.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.assignment.adapter.out.persistence.entity.AgencyMissionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AgencyMissionR2dbcRepository extends ReactiveCrudRepository<AgencyMissionEntity, UUID> {

    Mono<AgencyMissionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Mono<AgencyMissionEntity> findByCoreMissionIdAndTenantId(UUID coreMissionId, UUID tenantId);

    Flux<AgencyMissionEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);

    Flux<AgencyMissionEntity> findByAgencyIdAndTenantIdAndStatus(UUID agencyId, UUID tenantId, String status);

    Flux<AgencyMissionEntity> findByAssignedDelivererIdAndTenantId(UUID delivererId, UUID tenantId);
}
