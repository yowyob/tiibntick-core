package com.yowyob.tiibntick.core.actor.domain.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Domain model for an independent freelancer deliverer profile in TiiBnTick.
 *
 * <p>A freelancer is not permanently attached to an agency; they respond to
 * client delivery announcements and can optionally associate with agencies.
 *
 * <p>Kernel integration: {@code actorId} references the actor entity in
 * {@code RT-comops-actor-core} (UUID key). No Java inheritance from any Kernel class.
 *
 * <h3> — tnt-incident-core integration</h3>
 * <ul>
 *   <li>{@link #incidentHistoryCount} — counter incremented each time an incident
 *       involving this freelancer is closed. Used by
 *       {@code IActorReputationPort.getIncidentHistoryCount()}.</li>
 * </ul>
 *
 * <h3> — FreelancerOrganization integration (tnt-organization-core)</h3>
 * <ul>
 *   <li>{@link #freelancerOrgId} — UUID reference to the {@code FreelancerOrganization}
 *       this actor belongs to (nullable — a standalone freelancer has no org link).</li>
 *   <li>{@link #roleInOrg} — role within the org: {@link FreelancerRole#OWNER} or
 *       {@link FreelancerRole#SUB_DELIVERER}. Null when no org link.</li>
 *   <li>{@link #isOrgVerified} — cached flag indicating whether the linked org has
 *       passed admin verification. Updated by {@code FreelancerOrgEventConsumer}
 *       when {@code tnt.freelancer_org.verified} is received.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public final class FreelancerProfile extends TntActorProfile {

    private final List<ServiceZoneId> serviceZoneIds;
    private final List<AvailabilitySlot> availabilitySlots;
    private final UUID pricingPolicyId;
    private final Set<UUID> associatedAgencyIds;

    /**
     * Number of incidents in which this freelancer was involved.
     * Incremented by {@code IncidentEventConsumer} on {@code tnt.incident.closed}.
     */
    private final int incidentHistoryCount;

    // FreelancerOrganization link ────────────────────────────────────

    /**
     * UUID of the {@code FreelancerOrganization} this actor is linked to.
     * Null when the freelancer operates independently (no org membership).
     * References {@code tnt_freelancer_organization.id} in {@code tnt-organization-core}
     * (logical reference — no physical FK across module schemas).
     */
    private final UUID freelancerOrgId;

    /**
     * Role of this actor within the linked FreelancerOrganization.
     * {@link FreelancerRole#OWNER} if the actor created the org;
     * {@link FreelancerRole#SUB_DELIVERER} if they were invited and accepted.
     * Null when {@link #freelancerOrgId} is null.
     */
    private final FreelancerRole roleInOrg;

    /**
     * Cached verification status of the linked FreelancerOrganization.
     * {@code true} when the org has status {@code VERIFIED} or {@code ACTIVE}.
     * Updated reactively by {@code FreelancerOrgEventConsumer} when
     * {@code tnt.freelancer_org.verified} is received.
     * Always {@code false} when {@link #freelancerOrgId} is null.
     */
    private final boolean isOrgVerified;

    private FreelancerProfile(
            UUID id,
            UUID tenantId,
            UUID actorId,
            ActorStatus actorStatus,
            KycStatus kycStatus,
            ActorLocation currentLocation,
            ActorRating rating,
            Set<Badge> badges,
            Instant createdAt,
            Instant updatedAt,
            List<ServiceZoneId> serviceZoneIds,
            List<AvailabilitySlot> availabilitySlots,
            UUID pricingPolicyId,
            Set<UUID> associatedAgencyIds,
            int incidentHistoryCount,
            UUID freelancerOrgId,
            FreelancerRole roleInOrg,
            boolean isOrgVerified) {
        super(id, tenantId, actorId, ActorType.FREELANCER, actorStatus, kycStatus,
                currentLocation, rating, badges, createdAt, updatedAt);
        this.serviceZoneIds = serviceZoneIds != null ? List.copyOf(serviceZoneIds) : List.of();
        this.availabilitySlots = availabilitySlots != null
                ? List.copyOf(availabilitySlots) : List.of();
        this.pricingPolicyId = pricingPolicyId;
        this.associatedAgencyIds = associatedAgencyIds != null
                ? Set.copyOf(associatedAgencyIds) : Set.of();
        this.incidentHistoryCount = Math.max(0, incidentHistoryCount);
        this.freelancerOrgId = freelancerOrgId;
        this.roleInOrg = roleInOrg;
        this.isOrgVerified = freelancerOrgId != null && isOrgVerified;
    }

    // ── Factory methods ────────────────────────────────────────────────────────

    /**
     * Creates a new standalone freelancer profile (no org link).
     */
    public static FreelancerProfile create(
            UUID tenantId,
            UUID actorId,
            List<ServiceZoneId> serviceZoneIds,
            List<AvailabilitySlot> availabilitySlots) {
        return new FreelancerProfile(
                UUID.randomUUID(), tenantId, actorId,
                ActorStatus.INACTIVE, KycStatus.PENDING,
                null, ActorRating.zero(), Set.of(),
                Instant.now(), Instant.now(),
                serviceZoneIds, availabilitySlots, null, Set.of(),
                0, null, null, false);
    }

    /**
     * Rehydrates a FreelancerProfile from persistence (full constructor for the adapter).
     * Backward-compatible — {@code freelancerOrgId}, {@code roleInOrg} and
     * {@code isOrgVerified} are nullable/defaultable.
     */
    public static FreelancerProfile rehydrate(
            UUID id, UUID tenantId, UUID actorId,
            String actorStatus, String kycStatus,
            Double locationLat, Double locationLng, Double locationAccuracy,
            Instant locationTimestamp, String locationSource,
            double ratingScore, int ratingTotal, Instant ratingUpdatedAt,
            Set<Badge> badges,
            Instant createdAt, Instant updatedAt,
            List<ServiceZoneId> serviceZoneIds,
            List<AvailabilitySlot> availabilitySlots,
            UUID pricingPolicyId,
            Set<UUID> associatedAgencyIds,
            int incidentHistoryCount,
            UUID freelancerOrgId,
            String roleInOrg,
            boolean isOrgVerified) {
        ActorLocation location = (locationLat != null && locationLng != null)
                ? ActorLocation.of(locationLat, locationLng, locationAccuracy,
                        locationTimestamp != null ? locationTimestamp : Instant.now(),
                        LocationSource.from(locationSource))
                : null;
        ActorRating rating = ratingTotal > 0
                ? ActorRating.of(ratingScore, ratingTotal, ratingUpdatedAt)
                : ActorRating.zero();
        return new FreelancerProfile(
                id, tenantId, actorId,
                ActorStatus.from(actorStatus), KycStatus.from(kycStatus),
                location, rating, badges, createdAt, updatedAt,
                serviceZoneIds, availabilitySlots, pricingPolicyId, associatedAgencyIds,
                incidentHistoryCount,
                freelancerOrgId,
                FreelancerRole.from(roleInOrg),
                isOrgVerified);
    }

    // ── Domain mutations ───────────────────────────────────────────────────────

    public FreelancerProfile withLocation(ActorLocation location) {
        return new FreelancerProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                location, rating(), badges(), createdAt(), Instant.now(),
                serviceZoneIds, availabilitySlots, pricingPolicyId, associatedAgencyIds,
                incidentHistoryCount, freelancerOrgId, roleInOrg, isOrgVerified);
    }

    public FreelancerProfile withRating(ActorRating rating) {
        return new FreelancerProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating, badges(), createdAt(), Instant.now(),
                serviceZoneIds, availabilitySlots, pricingPolicyId, associatedAgencyIds,
                incidentHistoryCount, freelancerOrgId, roleInOrg, isOrgVerified);
    }

    public FreelancerProfile withKycStatus(KycStatus kycStatus) {
        return new FreelancerProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus,
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                serviceZoneIds, availabilitySlots, pricingPolicyId, associatedAgencyIds,
                incidentHistoryCount, freelancerOrgId, roleInOrg, isOrgVerified);
    }

    public FreelancerProfile activate() { return withStatus(ActorStatus.ACTIVE); }
    public FreelancerProfile deactivate() { return withStatus(ActorStatus.INACTIVE); }

    /**
     * Suspends this freelancer (called when fraud is flagged or org is suspended).
     *
     * @param reason audit reason (not persisted)
     * @return new suspended profile
     */
    public FreelancerProfile suspend(String reason) {
        return withStatus(ActorStatus.SUSPENDED);
    }

    private FreelancerProfile withStatus(ActorStatus status) {
        return new FreelancerProfile(id(), tenantId(), actorId(), status, kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                serviceZoneIds, availabilitySlots, pricingPolicyId, associatedAgencyIds,
                incidentHistoryCount, freelancerOrgId, roleInOrg, isOrgVerified);
    }

    public FreelancerProfile withBadge(Badge badge) {
        Set<Badge> updatedBadges = new LinkedHashSet<>(badges());
        updatedBadges.add(badge);
        return new FreelancerProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), updatedBadges, createdAt(), Instant.now(),
                serviceZoneIds, availabilitySlots, pricingPolicyId, associatedAgencyIds,
                incidentHistoryCount, freelancerOrgId, roleInOrg, isOrgVerified);
    }

    public FreelancerProfile associateWithAgency(UUID agencyId) {
        Objects.requireNonNull(agencyId, "agencyId must not be null");
        Set<UUID> updated = new LinkedHashSet<>(associatedAgencyIds);
        updated.add(agencyId);
        return new FreelancerProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                serviceZoneIds, availabilitySlots, pricingPolicyId, Set.copyOf(updated),
                incidentHistoryCount, freelancerOrgId, roleInOrg, isOrgVerified);
    }

    public FreelancerProfile dissociateFromAgency(UUID agencyId) {
        Objects.requireNonNull(agencyId, "agencyId must not be null");
        Set<UUID> updated = new LinkedHashSet<>(associatedAgencyIds);
        updated.remove(agencyId);
        return new FreelancerProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                serviceZoneIds, availabilitySlots, pricingPolicyId, Set.copyOf(updated),
                incidentHistoryCount, freelancerOrgId, roleInOrg, isOrgVerified);
    }

    public FreelancerProfile withAvailabilitySlots(List<AvailabilitySlot> slots) {
        return new FreelancerProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                serviceZoneIds, slots, pricingPolicyId, associatedAgencyIds,
                incidentHistoryCount, freelancerOrgId, roleInOrg, isOrgVerified);
    }

    public FreelancerProfile withServiceZones(List<ServiceZoneId> zones) {
        return new FreelancerProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                zones, availabilitySlots, pricingPolicyId, associatedAgencyIds,
                incidentHistoryCount, freelancerOrgId, roleInOrg, isOrgVerified);
    }

    /**
     * Returns a new profile with the incident history counter incremented by 1.
     *
     * @return new profile with incremented incident count
     */
    public FreelancerProfile withIncrementedIncidentCount() {
        return new FreelancerProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                serviceZoneIds, availabilitySlots, pricingPolicyId, associatedAgencyIds,
                incidentHistoryCount + 1, freelancerOrgId, roleInOrg, isOrgVerified);
    }

    // FreelancerOrganization link mutations ───────────────────────────

    /**
     * Links this freelancer profile to a FreelancerOrganization.
     *
     * <p>Called when:
     * <ul>
     *   <li>A OWNER registers their FreelancerOrganization (role = OWNER).</li>
     *   <li>A sub-deliverer accepts an invitation (role = SUB_DELIVERER).</li>
     * </ul>
     *
     * @param orgId         UUID of the FreelancerOrganization
     * @param role          the actor's role within the org
     * @param orgVerified   whether the org has already been verified by admin
     * @return new profile with org link set
     * @throws IllegalArgumentException if orgId or role is null
     */
    public FreelancerProfile withFreelancerOrgLink(UUID orgId, FreelancerRole role,
                                                     boolean orgVerified) {
        Objects.requireNonNull(orgId, "freelancerOrgId must not be null");
        Objects.requireNonNull(role, "roleInOrg must not be null");
        return new FreelancerProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                serviceZoneIds, availabilitySlots, pricingPolicyId, associatedAgencyIds,
                incidentHistoryCount, orgId, role, orgVerified);
    }

    /**
     * Updates the cached verification status of the linked FreelancerOrganization.
     *
     * <p>Called by {@code FreelancerOrgEventConsumer} when
     * {@code tnt.freelancer_org.verified} is received from tnt-organization-core.
     *
     * @param verified the new verification status
     * @return new profile with updated isOrgVerified flag
     * @throws IllegalStateException if this profile has no FreelancerOrg link
     */
    public FreelancerProfile withOrgVerificationUpdate(boolean verified) {
        if (freelancerOrgId == null) {
            throw new IllegalStateException(
                    "Cannot update org verification: this profile has no FreelancerOrg link");
        }
        return new FreelancerProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                serviceZoneIds, availabilitySlots, pricingPolicyId, associatedAgencyIds,
                incidentHistoryCount, freelancerOrgId, roleInOrg, verified);
    }

    /**
     * Removes the FreelancerOrganization link from this profile.
     *
     * <p>Called when:
     * <ul>
     *   <li>A sub-deliverer's association is revoked by the OWNER.</li>
     *   <li>A sub-deliverer voluntarily leaves the org.</li>
     * </ul>
     * The OWNER's org link is typically only removed when the org is dissolved.
     *
     * @return new profile with org link cleared (freelancerOrgId = null)
     */
    public FreelancerProfile withoutFreelancerOrg() {
        return new FreelancerProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                serviceZoneIds, availabilitySlots, pricingPolicyId, associatedAgencyIds,
                incidentHistoryCount, null, null, false);
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    public boolean isAssociatedWith(UUID agencyId) {
        return associatedAgencyIds.contains(agencyId);
    }

    public boolean coversZone(ServiceZoneId zoneId) {
        return serviceZoneIds.contains(zoneId);
    }

    /** @return {@code true} if this freelancer is linked to a FreelancerOrganization */
    public boolean hasOrgLink() {
        return freelancerOrgId != null;
    }

    /** @return {@code true} if this actor is the OWNER of their FreelancerOrganization */
    public boolean isOrgOwner() {
        return roleInOrg == FreelancerRole.OWNER;
    }

    /** @return {@code true} if this actor is a SUB_DELIVERER in a FreelancerOrganization */
    public boolean isSubDeliverer() {
        return roleInOrg == FreelancerRole.SUB_DELIVERER;
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public List<ServiceZoneId> serviceZoneIds() { return serviceZoneIds; }
    public List<AvailabilitySlot> availabilitySlots() { return availabilitySlots; }
    public UUID pricingPolicyId() { return pricingPolicyId; }
    public Set<UUID> associatedAgencyIds() { return associatedAgencyIds; }
    public int incidentHistoryCount() { return incidentHistoryCount; }

    /** @return UUID of the linked FreelancerOrganization, or null if standalone */
    public UUID freelancerOrgId() { return freelancerOrgId; }

    /** @return role within the linked org, or null if no org link */
    public FreelancerRole roleInOrg() { return roleInOrg; }

    /** @return whether the linked FreelancerOrganization is verified by admin */
    public boolean isOrgVerified() { return isOrgVerified; }
}
