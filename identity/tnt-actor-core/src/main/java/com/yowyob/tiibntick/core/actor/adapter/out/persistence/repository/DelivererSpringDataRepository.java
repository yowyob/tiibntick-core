package com.yowyob.tiibntick.core.actor.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity.DelivererProfileEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link DelivererProfileEntity}.
 *
 * <p> — Added queries for tnt-auth-core and tnt-incident-core integration:
 * <ul>
 *   <li>{@link #findActorIdByUserId} — for {@code IYowAuthTntAdapter.resolveActorId()}</li>
 *   <li>{@link #findAgencyIdByActorId} — for {@code IYowAuthTntAdapter.resolveAgencyId()}</li>
 *   <li>{@link #findFirstByActorId} — for cross-tenant reputation queries</li>
 *   <li>{@link #incrementIncidentHistoryCount} — atomic counter update for incident tracking</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface DelivererSpringDataRepository
        extends ReactiveCrudRepository<DelivererProfileEntity, UUID> {

    Mono<Boolean> existsByTenantIdAndActorId(UUID tenantId, UUID actorId);

    Mono<DelivererProfileEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Mono<DelivererProfileEntity> findByTenantIdAndActorId(UUID tenantId, UUID actorId);

    Flux<DelivererProfileEntity> findAllByTenantIdAndAgencyId(UUID tenantId, UUID agencyId);

    Flux<DelivererProfileEntity> findAllByTenantIdAndBranchId(UUID tenantId, UUID branchId);

    @Query("""
            SELECT * FROM tnt_actor.deliverer_profiles
            WHERE tenant_id = :tenantId
              AND branch_id = :branchId
              AND actor_status = 'ACTIVE'
              AND mission_active_id IS NULL
            """)
    Flux<DelivererProfileEntity> findAvailableByTenantIdAndBranchId(UUID tenantId, UUID branchId);

    @Query("""
            SELECT * FROM tnt_actor.deliverer_profiles
            WHERE tenant_id = :tenantId
              AND actor_status = 'ACTIVE'
              AND mission_active_id IS NULL
              AND capacity_kg >= :minCapacityKg
              AND location_lat IS NOT NULL
              AND 6371 * acos(
                  cos(radians(:latitude)) * cos(radians(location_lat)) *
                  cos(radians(location_lng) - radians(:longitude)) +
                  sin(radians(:latitude)) * sin(radians(location_lat))
              ) <= :radiusKm
            ORDER BY 6371 * acos(
                  cos(radians(:latitude)) * cos(radians(location_lat)) *
                  cos(radians(location_lng) - radians(:longitude)) +
                  sin(radians(:latitude)) * sin(radians(location_lat))
              ) ASC
            """)
    Flux<DelivererProfileEntity> findAvailableNear(UUID tenantId, double latitude, double longitude,
                                                    double radiusKm, double minCapacityKg);

    // ── tnt-auth-core: IYowAuthTntAdapter methods ─────────────────────────────

    /**
     * Returns the actor_id for the deliverer with the given actor_id (=userId) in a tenant.
     * Since actor_id == userId in the current design, this acts as an existence check
     * that returns the actorId for tnt-auth-core context enrichment.
     *
     * @param userId   the user UUID (JWT subject = actor_id in deliverer_profiles)
     * @param tenantId the tenant scope
     * @return the actor_id UUID if a deliverer profile exists for this user
     */
    @Query("""
            SELECT actor_id FROM tnt_actor.deliverer_profiles
            WHERE actor_id = :userId
              AND tenant_id = :tenantId
            LIMIT 1
            """)
    Mono<UUID> findActorIdByUserId(UUID userId, UUID tenantId);

    /**
     * Returns the agency_id of the deliverer identified by the given actor_id within a tenant.
     * Used by {@code IYowAuthTntAdapter.resolveAgencyId()} to enrich TntSecurityContext.
     *
     * @param actorId  the actor UUID
     * @param tenantId the tenant scope
     * @return the agency_id UUID if found
     */
    @Query("""
            SELECT agency_id FROM tnt_actor.deliverer_profiles
            WHERE actor_id = :actorId
              AND tenant_id = :tenantId
            LIMIT 1
            """)
    Mono<UUID> findAgencyIdByActorId(UUID actorId, UUID tenantId);

    // ── tnt-incident-core: IActorReputationPort methods ───────────────────────

    /**
     * Finds a deliverer by actor_id without tenant scoping.
     * Used for cross-module reputation queries from tnt-incident-core where
     * only the actorId is available (no tenant context).
     *
     * @param actorId the actor UUID (globally unique)
     * @return the deliverer profile entity if found
     */
    @Query("""
            SELECT * FROM tnt_actor.deliverer_profiles
            WHERE actor_id = :actorId
            LIMIT 1
            """)
    Mono<DelivererProfileEntity> findFirstByActorId(UUID actorId);

    /**
     * Atomically increments the incident_history_count for the deliverer with
     * the given actorId. Using a single UPDATE statement ensures atomicity
     * even under concurrent incident closures.
     *
     * @param actorId  the actor UUID of the deliverer
     * @param tenantId the tenant scope (for index efficiency)
     */
    @Modifying
    @Query("""
            UPDATE tnt_actor.deliverer_profiles
            SET incident_history_count = incident_history_count + 1,
                updated_at = NOW()
            WHERE actor_id = :actorId
              AND tenant_id = :tenantId
            """)
    Mono<Void> incrementIncidentHistoryCount(UUID actorId, UUID tenantId);
}
