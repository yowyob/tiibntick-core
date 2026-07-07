package com.yowyob.tiibntick.core.organization.application.port.in;

import com.yowyob.tiibntick.core.organization.domain.model.FreelancerOrganization;
import com.yowyob.tiibntick.core.organization.domain.vo.AssociatedDelivererRef;
import com.yowyob.tiibntick.core.organization.domain.vo.FreelancerCapabilities;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.organization.domain.vo.ServiceZone;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Primary inbound port — FreelancerOrganization management use cases.
 *
 * <p>Defines the contract exposed by the application layer for operations on
 * the {@link FreelancerOrganization} aggregate. Called by REST adapters,
 * Kafka consumers, or any other inbound adapter.
 *
 * <p>All operations are reactive (Project Reactor {@link Mono}/{@link Flux}).
 *
 * @author MANFOUO Braun
 */
public interface ManageFreelancerOrgUseCase {

    // ─── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers a new FreelancerOrganization.
     *
     * <p>Creates the aggregate in {@link com.yowyob.tiibntick.core.organization.domain.enums.FreelancerRegStatus#REGISTRATION_PENDING}
     * status with {@link com.yowyob.tiibntick.core.organization.domain.enums.KycLevel#NONE}.
     * Publishes a {@link com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgCreatedEvent}.
     *
     * @param organizationId  optional Kernel org UUID (nullable for direct registration)
     * @param ownerActorId    OWNER actor UUID (must exist in tnt-actor-core)
     * @param tradeName       commercial trade name
     * @return a {@link Mono} emitting the persisted {@link FreelancerOrganization}
     */
    Mono<FreelancerOrganization> registerFreelancerOrg(UUID organizationId,
                                                        UUID ownerActorId,
                                                        String tradeName);

    // ─── KYC lifecycle ────────────────────────────────────────────────────────

    /**
     * Upgrades the KYC level to BASIC (national ID photo submitted and validated).
     *
     * @param orgId the FreelancerOrganization internal ID
     * @return updated aggregate
     */
    Mono<FreelancerOrganization> upgradeKycToBasic(OrganizationId orgId);

    /**
     * Upgrades the KYC level to FULL (vehicle registration + insurance validated).
     *
     * @param orgId the FreelancerOrganization internal ID
     * @return updated aggregate
     */
    Mono<FreelancerOrganization> upgradeKycToFull(OrganizationId orgId);

    // ─── Admin lifecycle ──────────────────────────────────────────────────────

    /**
     * Submits the organization for admin review.
     *
     * @param orgId the FreelancerOrganization internal ID
     * @return updated aggregate
     */
    Mono<FreelancerOrganization> submitForReview(OrganizationId orgId);

    /**
     * Verifies (approves) the FreelancerOrganization.
     * Triggers DID issuance via tnt-trust.
     *
     * @param orgId       the FreelancerOrganization internal ID
     * @param adminActorId the admin performing the verification
     * @return updated aggregate
     */
    Mono<FreelancerOrganization> verifyFreelancerOrg(OrganizationId orgId, UUID adminActorId);

    /**
     * Activates a verified FreelancerOrganization.
     *
     * @param orgId the FreelancerOrganization internal ID
     * @return updated aggregate
     */
    Mono<FreelancerOrganization> activateFreelancerOrg(OrganizationId orgId);

    /**
     * Suspends a FreelancerOrganization (temporary non-compliance).
     *
     * @param orgId        the FreelancerOrganization internal ID
     * @param reason       human-readable suspension reason
     * @param adminActorId the admin performing the suspension (nullable)
     * @return updated aggregate
     */
    Mono<FreelancerOrganization> suspendFreelancerOrg(OrganizationId orgId,
                                                       String reason,
                                                       UUID adminActorId);

    /**
     * Reactivates a suspended FreelancerOrganization.
     *
     * @param orgId the FreelancerOrganization internal ID
     * @return updated aggregate
     */
    Mono<FreelancerOrganization> unsuspendFreelancerOrg(OrganizationId orgId);

    /**
     * Permanently blacklists a FreelancerOrganization.
     *
     * @param orgId        the FreelancerOrganization internal ID
     * @param adminActorId the admin performing the action
     * @return updated aggregate
     */
    Mono<FreelancerOrganization> blacklistFreelancerOrg(OrganizationId orgId, UUID adminActorId);

    // ─── Profile management ───────────────────────────────────────────────────

    /**
     * Updates the operational capabilities declared by the FreelancerOrganization.
     *
     * @param orgId        the FreelancerOrganization internal ID
     * @param capabilities the new capabilities
     * @return updated aggregate
     */
    Mono<FreelancerOrganization> updateCapabilities(OrganizationId orgId,
                                                     FreelancerCapabilities capabilities);

    /**
     * Replaces all operational service zones of the FreelancerOrganization.
     *
     * @param orgId the FreelancerOrganization internal ID
     * @param zones the new list of service zones
     * @return updated aggregate
     */
    Mono<FreelancerOrganization> updateOperationalZones(OrganizationId orgId,
                                                         List<ServiceZone> zones);

    /**
     * Updates the commercial trade name displayed to clients.
     *
     * @param orgId      the FreelancerOrganization internal ID
     * @param tradeName  the new trade name
     * @return updated aggregate
     */
    Mono<FreelancerOrganization> updateTradeName(OrganizationId orgId, String tradeName);

    // ─── Sub-deliverer management ─────────────────────────────────────────────

    /**
     * Invites a new sub-deliverer to join the FreelancerOrganization.
     *
     * @param orgId            FreelancerOrganization internal ID
     * @param delivererActorId actor UUID of the invited sub-deliverer
     * @param commissionRate   offered commission rate (0.0–1.0)
     * @return the newly created {@link AssociatedDelivererRef}
     */
    Mono<AssociatedDelivererRef> inviteSubDeliverer(OrganizationId orgId,
                                                     UUID delivererActorId,
                                                     BigDecimal commissionRate);

    /**
     * Accepts a sub-deliverer invitation (called when the sub-deliverer confirms).
     *
     * @param orgId            FreelancerOrganization internal ID
     * @param delivererActorId sub-deliverer actor UUID
     * @return the updated {@link AssociatedDelivererRef} in ACTIVE status
     */
    Mono<AssociatedDelivererRef> acceptSubDelivererInvitation(OrganizationId orgId,
                                                               UUID delivererActorId);

    /**
     * Revokes a sub-deliverer association.
     *
     * @param orgId            FreelancerOrganization internal ID
     * @param delivererActorId sub-deliverer actor UUID to revoke
     * @return the updated {@link AssociatedDelivererRef} in TERMINATED status
     */
    Mono<AssociatedDelivererRef> revokeSubDeliverer(OrganizationId orgId,
                                                     UUID delivererActorId);

    // ─── Queries ──────────────────────────────────────────────────────────────

    /**
     * Finds a FreelancerOrganization by its internal ID.
     *
     * @param id the internal ID
     * @return a {@link Mono} emitting the aggregate, or empty if not found
     */
    Mono<FreelancerOrganization> findById(OrganizationId id);

    /**
     * Finds a FreelancerOrganization by its OWNER actor UUID.
     *
     * @param ownerActorId the OWNER's actor UUID
     * @return a {@link Mono} emitting the aggregate, or empty if not found
     */
    Mono<FreelancerOrganization> findByOwnerActorId(UUID ownerActorId);

    /**
     * Returns all FreelancerOrganizations whose operational zones overlap
     * a given geographic point, within a search radius.
     *
     * @param latitude  WGS-84 latitude of the search point
     * @param longitude WGS-84 longitude of the search point
     * @param radiusKm  search radius in kilometers
     * @return a {@link Flux} of matching FreelancerOrganizations
     */
    Flux<FreelancerOrganization> findAvailableInZone(double latitude,
                                                      double longitude,
                                                      double radiusKm);

    /**
     * Lists all sub-deliverers of a FreelancerOrganization.
     *
     * @param orgId FreelancerOrganization internal ID
     * @return a {@link Flux} of {@link AssociatedDelivererRef}
     */
    Flux<AssociatedDelivererRef> listSubDeliverers(OrganizationId orgId);
}
