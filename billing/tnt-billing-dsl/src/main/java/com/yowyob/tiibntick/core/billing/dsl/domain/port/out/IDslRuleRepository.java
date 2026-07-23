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

    /**
     * Finds a rule by ID, scoped to a tenant.
     *
     * <p>Audit n°7 · #5 (IDOR) — callers resolving a single rule by ID on behalf of an
     * authenticated caller MUST use this method (never the unscoped {@link #findById}) so
     * that a caller cannot read or mutate another tenant's DSL rule by guessing or
     * enumerating UUIDs.
     *
     * @param id       the rule UUID
     * @param tenantId the tenant the rule must belong to
     * @return the matching rule, or empty if not found or owned by a different tenant
     */
    Mono<DslRule> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<DslRule> findByPolicyIdOrderByPriorityAsc(UUID policyId);

    Flux<DslRule> findActiveByPolicyIdOrderByPriorityAsc(UUID policyId);

    /**
     * Tenant-scoped variant of {@link #findByPolicyIdOrderByPriorityAsc}
     * (Audit n°7 · #5 — IDOR fix): a policyId alone does not prove the caller owns
     * the parent policy.
     */
    Flux<DslRule> findByPolicyIdAndTenantIdOrderByPriorityAsc(UUID policyId, UUID tenantId);

    /**
     * Tenant-scoped variant of {@link #findActiveByPolicyIdOrderByPriorityAsc}
     * (Audit n°7 · #5 — IDOR fix).
     */
    Flux<DslRule> findActiveByPolicyIdAndTenantIdOrderByPriorityAsc(UUID policyId, UUID tenantId);

    Flux<DslRule> findByTenantId(UUID tenantId);

    Mono<Void> deleteById(UUID id);

    Mono<Boolean> existsById(UUID id);
}
