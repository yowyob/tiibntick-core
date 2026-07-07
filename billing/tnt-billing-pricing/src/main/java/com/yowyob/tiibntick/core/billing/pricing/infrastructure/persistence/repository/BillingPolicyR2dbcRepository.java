package com.yowyob.tiibntick.core.billing.pricing.infrastructure.persistence.repository;

import com.yowyob.tiibntick.core.billing.pricing.infrastructure.persistence.entity.BillingPolicyEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link BillingPolicyEntity}.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #findByOwnerActorId} — finds policies by owner actor UUID string</li>
 *   <li>{@link #findByOwnerType} — finds policies by owner type</li>
 *   <li>{@link #findByOwnerTypeAndOwnerActorId} — composite owner query</li>
 *   <li>{@link #findActiveByOwnerTypeAndTenantId} — active policies per owner type × tenant</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface BillingPolicyR2dbcRepository
        extends ReactiveCrudRepository<BillingPolicyEntity, UUID> {

    Flux<BillingPolicyEntity> findByTenantId(UUID tenantId);

    @Query("SELECT * FROM pricing.billing_policy WHERE tenant_id = :tenantId AND status = 'ACTIVE'")
    Flux<BillingPolicyEntity> findActiveByTenantId(UUID tenantId);

    @Query("SELECT * FROM pricing.billing_policy WHERE tenant_id = :tenantId AND is_default = true LIMIT 1")
    Mono<BillingPolicyEntity> findDefaultByTenantId(UUID tenantId);

    // Owner-scoped queries ───────────────────────────────────────────

    /**
     * Finds all policies belonging to the given owner actor (by ID string).
     * Returns policies of any status.
     *
     * @param ownerActorId UUID string of the policy owner
     * @return all matching policies
     */
    @Query("SELECT * FROM pricing.billing_policy WHERE owner_actor_id = :ownerActorId")
    Flux<BillingPolicyEntity> findByOwnerActorId(String ownerActorId);

    /**
     * Finds all policies of a given owner type (tenant-independent).
     *
     * @param ownerType the owner type string value (e.g. "FREELANCER_ORG")
     * @return all matching policies
     */
    @Query("SELECT * FROM pricing.billing_policy WHERE owner_type = :ownerType")
    Flux<BillingPolicyEntity> findByOwnerType(String ownerType);

    /**
     * Finds policies by owner type AND owner actor ID.
     * Used to retrieve policies for a specific FreelancerOrg, HubPoint, etc.
     *
     * @param ownerType    owner type string
     * @param ownerActorId owner actor UUID string
     * @return matching policies
     */
    @Query("SELECT * FROM pricing.billing_policy WHERE owner_type = :ownerType AND owner_actor_id = :ownerActorId")
    Flux<BillingPolicyEntity> findByOwnerTypeAndOwnerActorId(String ownerType, String ownerActorId);

    /**
     * Finds active policies of a given owner type within a tenant.
     *
     * @param ownerType owner type string
     * @param tenantId  tenant UUID
     * @return active policies matching the filter
     */
    @Query("SELECT * FROM pricing.billing_policy WHERE owner_type = :ownerType AND tenant_id = :tenantId AND status = 'ACTIVE'")
    Flux<BillingPolicyEntity> findActiveByOwnerTypeAndTenantId(String ownerType, UUID tenantId);
}
