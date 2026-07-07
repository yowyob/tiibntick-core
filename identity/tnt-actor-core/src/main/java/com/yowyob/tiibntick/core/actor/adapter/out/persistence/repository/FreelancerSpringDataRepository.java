package com.yowyob.tiibntick.core.actor.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity.FreelancerProfileEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link FreelancerProfileEntity}.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #findFirstByActorId} — cross-tenant lookup for reputation port</li>
 *   <li>{@link #incrementIncidentHistoryCount} — atomic counter update</li>
 * </ul>
 *
 * <h3> additions — FreelancerOrganization integration</h3>
 * <ul>
 *   <li>{@link #findSubDeliverersByOrgId} — lists sub-deliverer actors of an org</li>
 *   <li>{@link #findOwnerByOrgId} — finds the OWNER actor of an org</li>
 *   <li>{@link #findByActorIdAndFreelancerOrgId} — actor + org composite lookup</li>
 *   <li>{@link #updateOrgVerificationStatusForOrg} — bulk cache update</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface FreelancerSpringDataRepository
        extends ReactiveCrudRepository<FreelancerProfileEntity, UUID> {

    Mono<Boolean> existsByTenantIdAndActorId(UUID tenantId, UUID actorId);

    Mono<FreelancerProfileEntity> findByTenantIdAndActorId(UUID tenantId, UUID actorId);

    @Query("""
            SELECT * FROM tnt_actor.freelancer_profiles
            WHERE tenant_id = :tenantId
              AND actor_status = 'ACTIVE'
              AND service_zone_ids_json::text LIKE '%' || CAST(:serviceZoneId AS text) || '%'
            """)
    Flux<FreelancerProfileEntity> findActiveByServiceZone(UUID tenantId, UUID serviceZoneId);

    @Query("""
            SELECT * FROM tnt_actor.freelancer_profiles
            WHERE tenant_id = :tenantId
              AND associated_agency_ids_json::text LIKE '%' || CAST(:agencyId AS text) || '%'
            """)
    Flux<FreelancerProfileEntity> findByAssociatedAgency(UUID tenantId, UUID agencyId);

    // ── tnt-incident-core: IActorReputationPort methods ───────────────────────

    /**
     * Finds a freelancer by actor_id without tenant scoping.
     * Used for cross-module reputation queries where only actorId is available.
     */
    @Query("""
            SELECT * FROM tnt_actor.freelancer_profiles
            WHERE actor_id = :actorId
            LIMIT 1
            """)
    Mono<FreelancerProfileEntity> findFirstByActorId(UUID actorId);

    /**
     * Atomically increments the incident_history_count for the freelancer.
     */
    @Modifying
    @Query("""
            UPDATE tnt_actor.freelancer_profiles
            SET incident_history_count = incident_history_count + 1,
                updated_at = NOW()
            WHERE actor_id = :actorId
              AND tenant_id = :tenantId
            """)
    Mono<Void> incrementIncidentHistoryCount(UUID actorId, UUID tenantId);

    // FreelancerOrganization integration ─────────────────────────────

    /**
     * Finds all sub-deliverer profiles linked to the given FreelancerOrganization.
     *
     * @param orgId UUID of the FreelancerOrganization
     * @return all profiles with freelancer_org_id = orgId AND role_in_org = 'SUB_DELIVERER'
     */
    @Query("""
            SELECT * FROM tnt_actor.freelancer_profiles
            WHERE freelancer_org_id = :orgId
              AND role_in_org = 'SUB_DELIVERER'
            """)
    Flux<FreelancerProfileEntity> findSubDeliverersByOrgId(UUID orgId);

    /**
     * Finds the OWNER profile of the given FreelancerOrganization.
     *
     * @param orgId UUID of the FreelancerOrganization
     * @return the profile with freelancer_org_id = orgId AND role_in_org = 'OWNER'
     */
    @Query("""
            SELECT * FROM tnt_actor.freelancer_profiles
            WHERE freelancer_org_id = :orgId
              AND role_in_org = 'OWNER'
            LIMIT 1
            """)
    Mono<FreelancerProfileEntity> findOwnerByOrgId(UUID orgId);

    /**
     * Finds a freelancer by actor_id and org membership.
     *
     * @param actorId actor UUID
     * @param orgId   UUID of the FreelancerOrganization
     */
    @Query("""
            SELECT * FROM tnt_actor.freelancer_profiles
            WHERE actor_id = :actorId
              AND freelancer_org_id = :orgId
            LIMIT 1
            """)
    Mono<FreelancerProfileEntity> findByActorIdAndFreelancerOrgId(UUID actorId, UUID orgId);

    /**
     * Bulk updates the is_org_verified flag for all profiles linked to an org.
     * Called when tnt.freelancer_org.verified event is received.
     *
     * @param orgId    UUID of the FreelancerOrganization
     * @param verified new verification status
     */
    @Modifying
    @Query("""
            UPDATE tnt_actor.freelancer_profiles
            SET is_org_verified = :verified,
                updated_at = NOW()
            WHERE freelancer_org_id = :orgId
            """)
    Mono<Void> updateOrgVerificationStatusForOrg(UUID orgId, boolean verified);
}
