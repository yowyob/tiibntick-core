package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.out;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.IBillingPolicyRepository;
import com.yowyob.tiibntick.core.billing.pricing.infrastructure.persistence.mapper.BillingPolicyMapper;
import com.yowyob.tiibntick.core.billing.pricing.infrastructure.persistence.repository.BillingPolicyR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound persistence adapter for {@link IBillingPolicyRepository}.
 *
 * <h3> additions</h3>
 * <p>Implements the three new owner-scoped query methods:
 * {@link #findByOwnerActorId}, {@link #findByOwnerTypeAndOwnerId},
 * {@link #findActiveByOwnerTypeAndTenantId}.</p>
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class BillingPolicyRepositoryAdapter implements IBillingPolicyRepository {

    private final BillingPolicyR2dbcRepository r2dbcRepository;
    private final BillingPolicyMapper mapper;

    @Override
    public Mono<BillingPolicy> save(BillingPolicy policy) {
        return r2dbcRepository.existsById(policy.getId())
                .flatMap(exists -> {
                    var entity = mapper.toEntity(policy);
                    entity.setNew(!exists);
                    return r2dbcRepository.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<BillingPolicy> findById(UUID id) {
        return r2dbcRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Mono<BillingPolicy> findByIdAndTenantId(UUID id, UUID tenantId) {
        return r2dbcRepository.findByIdAndTenantId(id, tenantId).map(mapper::toDomain);
    }

    @Override
    public Mono<BillingPolicy> findDefaultByTenantId(UUID tenantId) {
        return r2dbcRepository.findDefaultByTenantId(tenantId).map(mapper::toDomain);
    }

    @Override
    public Flux<BillingPolicy> findByTenantId(UUID tenantId) {
        return r2dbcRepository.findByTenantId(tenantId).map(mapper::toDomain);
    }

    @Override
    public Flux<BillingPolicy> findActiveByTenantId(UUID tenantId) {
        return r2dbcRepository.findActiveByTenantId(tenantId).map(mapper::toDomain);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return r2dbcRepository.existsById(id);
    }

    // Owner-scoped queries ───────────────────────────────────────────

    @Override
    public Flux<BillingPolicy> findByOwnerActorId(String ownerActorId) {
        return r2dbcRepository.findByOwnerActorId(ownerActorId).map(mapper::toDomain);
    }

    @Override
    public Flux<BillingPolicy> findByOwnerTypeAndOwnerId(PolicyOwnerType ownerType,
                                                           String ownerActorId) {
        return r2dbcRepository.findByOwnerTypeAndOwnerActorId(
                ownerType.name(), ownerActorId).map(mapper::toDomain);
    }

    @Override
    public Flux<BillingPolicy> findActiveByOwnerTypeAndTenantId(PolicyOwnerType ownerType,
                                                                  UUID tenantId) {
        return r2dbcRepository.findActiveByOwnerTypeAndTenantId(
                ownerType.name(), tenantId).map(mapper::toDomain);
    }
}
