package com.yowyob.tiibntick.core.linkback.adapter.out.persistence;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.TrustLinkEntity;
import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository.TrustLinkR2dbcRepository;
import com.yowyob.tiibntick.core.linkback.application.port.out.TrustLinkRepository;
import com.yowyob.tiibntick.core.linkback.domain.model.TrustLink;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TrustLinkPersistenceAdapter implements TrustLinkRepository {

    private final TrustLinkR2dbcRepository r2dbcRepository;

    @Override
    public Mono<TrustLink> save(TrustLink link) {
        TrustLinkEntity entity = TrustLinkEntity.builder()
                .id(link.getId())
                .isNew(true)
                .tenantId(link.getTenantId())
                .fromNodeId(link.getFromNodeId())
                .toNodeId(link.getToNodeId())
                .createdAt(link.getCreatedAt())
                .build();
        return r2dbcRepository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByFromAndTo(UUID tenantId, UUID fromNodeId, UUID toNodeId) {
        return r2dbcRepository.existsByTenantIdAndFromNodeIdAndToNodeId(tenantId, fromNodeId, toNodeId);
    }

    @Override
    public Flux<TrustLink> findByToNodeId(UUID tenantId, UUID toNodeId) {
        return r2dbcRepository.findByTenantIdAndToNodeId(tenantId, toNodeId).map(this::toDomain);
    }

    @Override
    public Mono<Long> countByToNodeId(UUID tenantId, UUID toNodeId) {
        return r2dbcRepository.countByTenantIdAndToNodeId(tenantId, toNodeId);
    }

    private TrustLink toDomain(TrustLinkEntity entity) {
        return TrustLink.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .fromNodeId(entity.getFromNodeId())
                .toNodeId(entity.getToNodeId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
