package com.yowyob.tiibntick.core.billing.pricing.domain.port.out;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — billing policy persistence.
 *
 * <h3> additions — FreelancerOrg and multi-owner queries</h3>
 * <ul>
 *   <li>{@link #findByOwnerActorId} — finds policies owned by a given actor (e.g. FreelancerOrg)</li>
 *   <li>{@link #findByOwnerTypeAndOwnerId} — finds policies by owner type + actor ID</li>
 *   <li>{@link #findActiveByOwnerTypeAndTenantId} — active policies filter by owner type</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface IBillingPolicyRepository {

    Mono<BillingPolicy> save(BillingPolicy policy);

    Mono<BillingPolicy> findById(UUID id);

    Mono<BillingPolicy> findDefaultByTenantId(UUID tenantId);

    Flux<BillingPolicy> findByTenantId(UUID tenantId);

    Flux<BillingPolicy> findActiveByTenantId(UUID tenantId);

    Mono<Void> deleteById(UUID id);

    Mono<Boolean> existsById(UUID id);

    // Multi-owner queries ─────────────────────────────────────────────

    /**
     * Finds all policies whose {@code owner_actor_id} matches the given ID.
     * Used to retrieve the FreelancerOrg's own billing policy.
     *
     * @param ownerActorId UUID string of the policy owner actor
     * @return flux of matching policies
     */
    Flux<BillingPolicy> findByOwnerActorId(String ownerActorId);

    /**
     * Finds all policies of a given owner type belonging to a specific actor.
     *
     * @param ownerType   the type of policy owner
     * @param ownerActorId UUID string of the owner actor
     * @return flux of matching policies
     */
    Flux<BillingPolicy> findByOwnerTypeAndOwnerId(PolicyOwnerType ownerType, String ownerActorId);

    /**
     * Returns active policies of a given owner type within a tenant.
     * Used for list queries on the admin dashboard.
     *
     * @param ownerType the type of policy owner to filter
     * @param tenantId  the tenant scope
     * @return flux of active policies
     */
    Flux<BillingPolicy> findActiveByOwnerTypeAndTenantId(PolicyOwnerType ownerType, UUID tenantId);
}
