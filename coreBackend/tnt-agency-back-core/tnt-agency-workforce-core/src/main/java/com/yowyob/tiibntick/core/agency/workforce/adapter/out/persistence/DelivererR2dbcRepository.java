package com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.entity.DelivererEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DelivererR2dbcRepository extends ReactiveCrudRepository<DelivererEntity, UUID> {

    Mono<DelivererEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Mono<DelivererEntity> findByActorIdAndTenantId(UUID actorId, UUID tenantId);

    Flux<DelivererEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);

    @Query("SELECT * FROM agency_hr.deliverers WHERE tenant_id = :tenantId AND phone = :phone LIMIT 1")
    Mono<DelivererEntity> findByPhoneAndTenantId(String phone, UUID tenantId);
}
