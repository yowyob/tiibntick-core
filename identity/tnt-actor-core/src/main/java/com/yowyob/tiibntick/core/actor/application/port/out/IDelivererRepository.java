package com.yowyob.tiibntick.core.actor.application.port.out;

import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — deliverer profile persistence.
 *
 * <p> — Added methods required by:
 * <ul>
 *   <li>{@code ActorCoreYowAuthTntAdapter} (tnt-auth-core integration):
 *       {@link #findActorIdByUserId}, {@link #findAgencyIdByActorId}</li>
 *   <li>{@code ActorReputationPortAdapter} (tnt-incident-core integration):
 *       {@link #findFirstByActorId}, {@link #incrementIncidentHistoryCount}</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface IDelivererRepository {

    Mono<Boolean> existsByActorId(UUID tenantId, UUID actorId);

    Mono<DelivererProfile> findById(UUID tenantId, UUID id);

    Mono<DelivererProfile> findByActorId(UUID tenantId, UUID actorId);

    Flux<DelivererProfile> findByAgencyId(UUID tenantId, UUID agencyId);

    Flux<DelivererProfile> findByBranchId(UUID tenantId, UUID branchId);

    Flux<DelivererProfile> findAvailableByBranchId(UUID tenantId, UUID branchId);

    Flux<DelivererProfile> findAvailableNear(UUID tenantId, double latitude, double longitude,
                                              double radiusKm, double minCapacityKg);

    Mono<DelivererProfile> save(DelivererProfile profile);

    // ── tnt-auth-core integration (ActorCoreYowAuthTntAdapter) ─────────────────

    /**
     * Finds the actor ID (=userId in current design) of a deliverer identified by their
     * user ID within a given tenant. Used by {@code IYowAuthTntAdapter.resolveActorId()}
     * to enrich the {@code TntSecurityContext} with the actor profile UUID.
     *
     * @param userId   the authenticated user UUID (JWT subject)
     * @param tenantId the tenant scope
     * @return the actor_id UUID if a deliverer profile exists for this user, empty otherwise
     */
    Mono<UUID> findActorIdByUserId(UUID userId, UUID tenantId);

    /**
     * Finds the agency ID of a deliverer identified by their actor ID and tenant.
     * Used by {@code IYowAuthTntAdapter.resolveAgencyId()} to enrich the
     * {@code TntSecurityContext} with the agency UUID.
     *
     * @param actorId  the actor UUID (JWT claim "actor")
     * @param tenantId the tenant scope
     * @return the agency_id UUID if found, empty otherwise
     */
    Mono<UUID> findAgencyIdByActorId(UUID actorId, UUID tenantId);

    // ── tnt-incident-core integration (ActorReputationPortAdapter) ─────────────

    /**
     * Finds a deliverer by actor ID without tenant scoping.
     * Used by {@code IActorReputationPort} which receives only the actorId
     * from {@code tnt-incident-core} (cross-module, no tenant context available).
     *
     * @param actorId the actor UUID (globally unique)
     * @return the deliverer profile if found, empty otherwise
     */
    Mono<DelivererProfile> findFirstByActorId(UUID actorId);

    /**
     * Atomically increments the {@code incident_history_count} column for
     * the deliverer identified by the given actorId.
     * Called by {@code IncidentEventConsumer} when {@code tnt.incident.closed}
     * is received and this deliverer was the primary driver.
     *
     * @param actorId  the actor UUID of the deliverer
     * @param tenantId the tenant scope (for index efficiency)
     * @return empty Mono on success
     */
    Mono<Void> incrementIncidentHistoryCount(UUID actorId, UUID tenantId);
}
