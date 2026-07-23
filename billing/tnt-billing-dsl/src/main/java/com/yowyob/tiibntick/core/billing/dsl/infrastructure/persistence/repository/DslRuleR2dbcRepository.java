package com.yowyob.tiibntick.core.billing.dsl.infrastructure.persistence.repository;

import com.yowyob.tiibntick.core.billing.dsl.infrastructure.persistence.entity.DslRuleEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link DslRuleEntity}.
 *
 * @author MANFOUO Braun
 */
public interface DslRuleR2dbcRepository extends ReactiveCrudRepository<DslRuleEntity, UUID> {

    @Query("SELECT * FROM billing.dsl_rule WHERE policy_id = :policyId ORDER BY priority ASC")
    Flux<DslRuleEntity> findByPolicyIdOrderByPriorityAsc(UUID policyId);

    @Query("SELECT * FROM billing.dsl_rule WHERE policy_id = :policyId AND active = true ORDER BY priority ASC")
    Flux<DslRuleEntity> findActiveByPolicyIdOrderByPriorityAsc(UUID policyId);

    /**
     * Tenant-scoped variant of {@link #findByPolicyIdOrderByPriorityAsc} (Audit n°7 · #5 —
     * IDOR fix): a policyId alone does not prove the caller owns the parent policy.
     */
    @Query("SELECT * FROM billing.dsl_rule WHERE policy_id = :policyId AND tenant_id = :tenantId ORDER BY priority ASC")
    Flux<DslRuleEntity> findByPolicyIdAndTenantIdOrderByPriorityAsc(UUID policyId, UUID tenantId);

    /**
     * Tenant-scoped variant of {@link #findActiveByPolicyIdOrderByPriorityAsc}
     * (Audit n°7 · #5 — IDOR fix).
     */
    @Query("SELECT * FROM billing.dsl_rule WHERE policy_id = :policyId AND tenant_id = :tenantId AND active = true ORDER BY priority ASC")
    Flux<DslRuleEntity> findActiveByPolicyIdAndTenantIdOrderByPriorityAsc(UUID policyId, UUID tenantId);

    @Query("SELECT * FROM billing.dsl_rule WHERE tenant_id = :tenantId ORDER BY priority ASC")
    Flux<DslRuleEntity> findByTenantId(UUID tenantId);

    /**
     * Finds a rule by ID scoped to a tenant (Audit n°7 · #5 — IDOR fix).
     *
     * @param id       the rule UUID
     * @param tenantId the tenant the rule must belong to
     * @return the matching entity, or empty if not found or owned by a different tenant
     */
    @Query("SELECT * FROM billing.dsl_rule WHERE id = :id AND tenant_id = :tenantId")
    Mono<DslRuleEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
