package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.TrustLinkEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TrustLinkR2dbcRepository extends ReactiveCrudRepository<TrustLinkEntity, UUID> {

    Mono<Boolean> existsByTenantIdAndFromNodeIdAndToNodeId(UUID tenantId, UUID fromNodeId, UUID toNodeId);

    Flux<TrustLinkEntity> findByTenantIdAndToNodeId(UUID tenantId, UUID toNodeId);

    Mono<Long> countByTenantIdAndToNodeId(UUID tenantId, UUID toNodeId);
}
