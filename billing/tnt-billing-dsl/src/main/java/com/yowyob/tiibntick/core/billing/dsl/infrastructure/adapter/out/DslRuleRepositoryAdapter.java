package com.yowyob.tiibntick.core.billing.dsl.infrastructure.adapter.out;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslRule;
import com.yowyob.tiibntick.core.billing.dsl.domain.port.out.IDslRuleRepository;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.persistence.mapper.DslRuleMapper;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.persistence.repository.DslRuleR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter (secondary / driven) bridging the domain port
 * {@link IDslRuleRepository} to the Spring Data R2DBC repository.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DslRuleRepositoryAdapter implements IDslRuleRepository {

    private final DslRuleR2dbcRepository r2dbcRepository;
    private final DslRuleMapper mapper;

    @Override
    public Mono<DslRule> save(DslRule rule) {
        return r2dbcRepository.existsById(rule.getId())
                .flatMap(exists -> {
                    var entity = mapper.toEntity(rule);
                    entity.setNew(!exists);
                    return r2dbcRepository.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<DslRule> findById(UUID id) {
        return r2dbcRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Flux<DslRule> findByPolicyIdOrderByPriorityAsc(UUID policyId) {
        return r2dbcRepository.findByPolicyIdOrderByPriorityAsc(policyId)
                .map(mapper::toDomain);
    }

    @Override
    public Flux<DslRule> findActiveByPolicyIdOrderByPriorityAsc(UUID policyId) {
        return r2dbcRepository.findActiveByPolicyIdOrderByPriorityAsc(policyId)
                .map(mapper::toDomain);
    }

    @Override
    public Flux<DslRule> findByTenantId(UUID tenantId) {
        return r2dbcRepository.findByTenantId(tenantId)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return r2dbcRepository.existsById(id);
    }
}
