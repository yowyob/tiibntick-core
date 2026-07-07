package com.yowyob.tiibntick.core.billing.dsl.domain.port.out;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslRule;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port (secondary / driven) for DSL rule persistence.
 * Implemented by the infrastructure layer (R2DBC adapter).
 *
 * @author MANFOUO Braun
 */
public interface IDslRuleRepository {

    Mono<DslRule> save(DslRule rule);

    Mono<DslRule> findById(UUID id);

    Flux<DslRule> findByPolicyIdOrderByPriorityAsc(UUID policyId);

    Flux<DslRule> findActiveByPolicyIdOrderByPriorityAsc(UUID policyId);

    Flux<DslRule> findByTenantId(UUID tenantId);

    Mono<Void> deleteById(UUID id);

    Mono<Boolean> existsById(UUID id);
}
