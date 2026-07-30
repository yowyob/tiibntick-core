package com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.entity.HubHandoffRequestEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface HubHandoffRequestR2dbcRepository extends ReactiveCrudRepository<HubHandoffRequestEntity, UUID> {

    Mono<HubHandoffRequestEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<HubHandoffRequestEntity> findByHubIdAndTenantIdAndStatus(UUID hubId, UUID tenantId, String status);

    Flux<HubHandoffRequestEntity> findByHubIdAndTenantIdOrderByCreatedAtDesc(UUID hubId, UUID tenantId);
}
