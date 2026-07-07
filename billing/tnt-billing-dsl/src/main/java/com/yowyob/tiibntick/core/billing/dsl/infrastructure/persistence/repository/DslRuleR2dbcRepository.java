package com.yowyob.tiibntick.core.billing.dsl.infrastructure.persistence.repository;

import com.yowyob.tiibntick.core.billing.dsl.infrastructure.persistence.entity.DslRuleEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

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

    @Query("SELECT * FROM billing.dsl_rule WHERE tenant_id = :tenantId ORDER BY priority ASC")
    Flux<DslRuleEntity> findByTenantId(UUID tenantId);
}
