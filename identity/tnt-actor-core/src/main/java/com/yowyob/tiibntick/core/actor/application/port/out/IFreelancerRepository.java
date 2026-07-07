package com.yowyob.tiibntick.core.actor.application.port.out;

import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — freelancer profile persistence.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@code ActorReputationPortAdapter} (tnt-incident-core integration):
 *       {@link #findFirstByActorId}, {@link #incrementIncidentHistoryCount}</li>
 * </ul>
 *
 * <h3> additions — FreelancerOrganization integration</h3>
 * <ul>
 *   <li>{@link #findSubDeliverersByOrgId} — lists all sub-deliverer actors of an org.</li>
 *   <li>{@link #findOwnerByOrgId} — finds the OWNER actor of an org.</li>
 *   <li>{@link #findByActorIdAndOrgId} — finds a specific actor within an org.</li>
 *   <li>{@link #updateOrgVerificationStatusForOrg} — bulk update of {@code isOrgVerified}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface IFreelancerRepository {

    Mono<Boolean> existsByActorId(UUID tenantId, UUID actorId);

    Mono<FreelancerProfile> findById(UUID tenantId, UUID id);

    Mono<FreelancerProfile> findByActorId(UUID tenantId, UUID actorId);

    Flux<FreelancerProfile> findActiveByServiceZone(UUID tenantId, UUID serviceZoneId);

    Flux<FreelancerProfile> findByAssociatedAgency(UUID tenantId, UUID agencyId);

    Mono<FreelancerProfile> save(FreelancerProfile profile);

    // ── tnt-incident-core integration (ActorReputationPortAdapter) ─────────────

    /**
     * Finds a freelancer by actor ID without tenant scoping.
     * Used by {@code IActorReputationPort} cross-module calls.
     *
     * @param actorId the actor UUID (globally unique)
     * @return the freelancer profile if found, empty otherwise
     */
    Mono<FreelancerProfile> findFirstByActorId(UUID actorId);

    /**
     * Atomically increments the {@code incident_history_count} for this freelancer.
     * Called by {@code IncidentEventConsumer} on {@code tnt.incident.closed}.
     *
     * @param actorId  the actor UUID of the freelancer
     * @param tenantId the tenant scope
     * @return empty Mono on success
     */
    Mono<Void> incrementIncidentHistoryCount(UUID actorId, UUID tenantId);

    // FreelancerOrganization integration ─────────────────────────────

    /**
     * Returns all sub-deliverer profiles (role = SUB_DELIVERER) linked to the
     * given FreelancerOrganization.
     *
     * @param orgId UUID of the FreelancerOrganization
     * @return a {@link Flux} of matching profiles
     */
    Flux<FreelancerProfile> findSubDeliverersByOrgId(UUID orgId);

    /**
     * Returns the OWNER profile (role = OWNER) of the given FreelancerOrganization.
     *
     * @param orgId UUID of the FreelancerOrganization
     * @return the owner profile, or empty if not found
     */
    Mono<FreelancerProfile> findOwnerByOrgId(UUID orgId);

    /**
     * Finds a specific freelancer profile by actorId and org membership.
     *
     * @param actorId actor UUID
     * @param orgId   UUID of the FreelancerOrganization
     * @return matching profile, or empty if not found
     */
    Mono<FreelancerProfile> findByActorIdAndOrgId(UUID actorId, UUID orgId);

    /**
     * Updates the {@code is_org_verified} flag for all profiles linked to the
     * given FreelancerOrganization (bulk update).
     *
     * <p>Called when {@code tnt.freelancer_org.verified} is received to keep
     * the local cache in sync with the org status in tnt-organization-core.
     *
     * @param orgId    UUID of the FreelancerOrganization
     * @param verified new verification status
     * @return empty Mono on success
     */
    Mono<Void> updateOrgVerificationStatusForOrg(UUID orgId, boolean verified);
}
