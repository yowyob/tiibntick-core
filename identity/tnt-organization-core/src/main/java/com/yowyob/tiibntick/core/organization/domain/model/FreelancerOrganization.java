package com.yowyob.tiibntick.core.organization.domain.model;

import com.yowyob.tiibntick.core.organization.domain.enums.AssociationStatus;
import com.yowyob.tiibntick.core.organization.domain.enums.FreelancerRegStatus;
import com.yowyob.tiibntick.core.organization.domain.enums.KycLevel;
import com.yowyob.tiibntick.core.organization.domain.vo.AssociatedDelivererRef;
import com.yowyob.tiibntick.core.organization.domain.vo.FreelancerBillingProfile;
import com.yowyob.tiibntick.core.organization.domain.vo.FreelancerCapabilities;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.organization.domain.vo.ServiceZone;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root representing a TiiBnTick Freelancer Organization.
 *
 * <p>A {@code FreelancerOrganization} models an independent delivery operator
 * (a "benski" or freelancer) as a first-class organization in TiiBnTick. Unlike a
 * simple actor profile, a FreelancerOrganization:
 * <ul>
 *   <li>Has its own <strong>multi-tenant context</strong> (prefixed tenant ID "FRL-{uuid}").</li>
 *   <li>Can declare a <strong>fleet</strong> of 1–3 vehicles (managed by {@code tnt-resource-core}).</li>
 *   <li>Can associate up to <strong>5 sub-deliverers</strong> who work under its banner.</li>
 *   <li>Defines its own <strong>billing policy</strong> (DSL Simplifié or template-based).</li>
 *   <li>Goes through a <strong>KYC lifecycle</strong> before being allowed to take missions.</li>
 * </ul>
 *
 * <p>Kernel integration: an optional {@code organizationId} may reference the Yowyob
 * Kernel Organization entity; however, unlike Agency/Branch, this reference is
 * <strong>nullable</strong> — a FreelancerOrganization can be created independently of the
 * Kernel org hierarchy (direct TiiBnTick registration flow).
 *
 * <p>Architecture: no Java inheritance from any Kernel class.
 * The domain logic is entirely self-contained in this aggregate.
 *
 * <p><strong>Sub-deliverer constraint:</strong> a FreelancerOrganization may have
 * at most {@value #MAX_SUB_DELIVERERS} sub-deliverers simultaneously ACTIVE or
 * PENDING_ACCEPTANCE.
 *
 * @author MANFOUO Braun
 */
public class FreelancerOrganization {

    /** Maximum number of sub-deliverers (ACTIVE + PENDING_ACCEPTANCE combined). */
    public static final int MAX_SUB_DELIVERERS = 5;

    // ─── Identity ────────────────────────────────────────────────────────────

    /** TiiBnTick internal FreelancerOrganization identifier. */
    private final OrganizationId id;

    /**
     * Optional Kernel Organization reference (RT-comops-organization-core UUID).
     * Nullable — FreelancerOrgs may be registered without a Kernel org link.
     */
    private final UUID organizationId;

    /**
     * Multi-tenant isolation key.
     * Always prefixed "FRL-{uuid}" to distinguish from Agency tenants.
     */
    private final String tenantId;

    /** Commercial trade name displayed to clients (e.g., "Moto Express Nlongkak"). */
    private String tradeName;

    /** UUID of the OWNER actor (from tnt-actor-core). Not null. */
    private final UUID ownerActorId;

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    /** Current registration status. Starts at REGISTRATION_PENDING. */
    private FreelancerRegStatus registrationStatus;

    /** Current KYC verification level. Starts at NONE. */
    private KycLevel kycLevel;

    // ─── Billing ─────────────────────────────────────────────────────────────

    /** Billing profile linking to the active BillingPolicy in tnt-billing-pricing. */
    private FreelancerBillingProfile billingProfile;

    // ─── Trust ───────────────────────────────────────────────────────────────

    /** Aggregated trust score (0.0–5.0) — updated by tnt-trust and tnt-actor-core. */
    private double trustScore;

    /** Blockchain DID identifier assigned after VERIFIED status (nullable until then). */
    private String blockchainDid;

    // ─── Capabilities ────────────────────────────────────────────────────────

    /** Declared operational capabilities (package types, weight, distance, specializations). */
    private FreelancerCapabilities capabilities;

    // ─── Collections (loaded/persisted by repository adapter) ────────────────

    /**
     * Declared operational service zones (polygonal coverage areas).
     * Mutable list — managed by the aggregate via business methods.
     */
    private List<ServiceZone> operationalZones;

    /**
     * Sub-deliverers associated with this org.
     * Enforces {@link #MAX_SUB_DELIVERERS} constraint.
     */
    private List<AssociatedDelivererRef> subDeliverers;

    // ─── Audit ───────────────────────────────────────────────────────────────

    /** Record creation timestamp (UTC). */
    private final Instant createdAt;

    /** Last modification timestamp (UTC). */
    private Instant updatedAt;

    /** Optimistic locking version. */
    private int version;

    // ─── Full constructor (used by repository adapter) ────────────────────────

    /**
     * Full constructor — used by repository adapters when reconstituting from persistence.
     *
     * @param id                 TiiBnTick internal ID
     * @param organizationId     Kernel org UUID (nullable)
     * @param tenantId           Multi-tenant key (prefixed "FRL-")
     * @param tradeName          Commercial trade name
     * @param ownerActorId       OWNER actor UUID (must not be null)
     * @param registrationStatus Current registration status
     * @param kycLevel           Current KYC level
     * @param billingProfile     Active billing profile
     * @param trustScore         Current trust score (0.0–5.0)
     * @param blockchainDid      Blockchain DID (nullable)
     * @param capabilities       Operational capabilities
     * @param operationalZones   Coverage zones (mutable list)
     * @param subDeliverers      Sub-deliverer associations (mutable list)
     * @param createdAt          Creation timestamp
     * @param updatedAt          Last update timestamp
     * @param version            Optimistic locking version
     */
    public FreelancerOrganization(OrganizationId id,
                                   UUID organizationId,
                                   String tenantId,
                                   String tradeName,
                                   UUID ownerActorId,
                                   FreelancerRegStatus registrationStatus,
                                   KycLevel kycLevel,
                                   FreelancerBillingProfile billingProfile,
                                   double trustScore,
                                   String blockchainDid,
                                   FreelancerCapabilities capabilities,
                                   List<ServiceZone> operationalZones,
                                   List<AssociatedDelivererRef> subDeliverers,
                                   Instant createdAt,
                                   Instant updatedAt,
                                   int version) {
        if (ownerActorId == null) {
            throw new IllegalArgumentException("FreelancerOrganization.ownerActorId must not be null");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("FreelancerOrganization.tenantId must not be blank");
        }
        this.id = id;
        this.organizationId = organizationId;
        this.tenantId = tenantId;
        this.tradeName = tradeName;
        this.ownerActorId = ownerActorId;
        this.registrationStatus = registrationStatus;
        this.kycLevel = kycLevel;
        this.billingProfile = billingProfile != null ? billingProfile : FreelancerBillingProfile.empty();
        this.trustScore = trustScore;
        this.blockchainDid = blockchainDid;
        this.capabilities = capabilities != null ? capabilities : FreelancerCapabilities.defaults();
        this.operationalZones = operationalZones != null ? new ArrayList<>(operationalZones) : new ArrayList<>();
        this.subDeliverers = subDeliverers != null ? new ArrayList<>(subDeliverers) : new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    // ─── Factory method ───────────────────────────────────────────────────────

    /**
     * Factory — registers a new FreelancerOrganization.
     *
     * <p>The new org starts with:
     * <ul>
     *   <li>status = {@link FreelancerRegStatus#REGISTRATION_PENDING}</li>
     *   <li>kycLevel = {@link KycLevel#NONE}</li>
     *   <li>billingProfile = empty (no policy yet)</li>
     *   <li>trustScore = 0.0</li>
     *   <li>capabilities = defaults (5 kg, 10 km)</li>
     *   <li>no zones, no sub-deliverers</li>
     * </ul>
     *
     * @param organizationId Kernel org UUID (nullable)
     * @param ownerActorId   OWNER actor UUID (must not be null)
     * @param tradeName      Commercial trade name
     * @return a new, unsaved {@link FreelancerOrganization}
     */
    public static FreelancerOrganization register(UUID organizationId,
                                                   UUID ownerActorId,
                                                   String tradeName) {
        Instant now = Instant.now();
        OrganizationId id = OrganizationId.generate();
        // Tenant ID is prefixed to distinguish freelancer tenants from agency tenants
        String tenantId = "FRL-" + id.value().toString();
        return new FreelancerOrganization(
                id, organizationId, tenantId, tradeName, ownerActorId,
                FreelancerRegStatus.REGISTRATION_PENDING, KycLevel.NONE,
                FreelancerBillingProfile.empty(), 0.0, null,
                FreelancerCapabilities.defaults(),
                new ArrayList<>(), new ArrayList<>(),
                now, now, 0);
    }

    // ─── KYC business methods ──────────────────────────────────────────────────

    /**
     * Upgrades KYC to BASIC (national ID photo validated).
     * Can only be applied when current level is {@link KycLevel#NONE}.
     *
     * @throws IllegalStateException if KYC is already BASIC or FULL
     */
    public void upgradeKycToBasic() {
        if (this.kycLevel != KycLevel.NONE) {
            throw new IllegalStateException(
                    "KYC already at level " + this.kycLevel + " — cannot downgrade to BASIC");
        }
        this.kycLevel = KycLevel.BASIC;
        this.updatedAt = Instant.now();
    }

    /**
     * Upgrades KYC to FULL (vehicle documents + insurance validated).
     * Can only be applied when current level is {@link KycLevel#BASIC}.
     *
     * @throws IllegalStateException if KYC is not at BASIC level
     */
    public void upgradeKycToFull() {
        if (this.kycLevel != KycLevel.BASIC) {
            throw new IllegalStateException(
                    "KYC must be BASIC before upgrading to FULL, current: " + this.kycLevel);
        }
        this.kycLevel = KycLevel.FULL;
        this.updatedAt = Instant.now();
    }

    // ─── Lifecycle business methods ────────────────────────────────────────────

    /**
     * Marks the organization as verified by an admin.
     * Transitions status from UNDER_REVIEW to VERIFIED.
     *
     * @throws IllegalStateException if not in UNDER_REVIEW or REGISTRATION_PENDING status
     */
    public void verify() {
        if (this.registrationStatus != FreelancerRegStatus.UNDER_REVIEW
                && this.registrationStatus != FreelancerRegStatus.REGISTRATION_PENDING) {
            throw new IllegalStateException(
                    "Cannot verify organization in status: " + this.registrationStatus);
        }
        this.registrationStatus = FreelancerRegStatus.VERIFIED;
        this.updatedAt = Instant.now();
    }

    /**
     * Submits the organization for admin review.
     * Transitions status from REGISTRATION_PENDING to UNDER_REVIEW.
     */
    public void submitForReview() {
        if (this.registrationStatus != FreelancerRegStatus.REGISTRATION_PENDING) {
            throw new IllegalStateException(
                    "Cannot submit for review from status: " + this.registrationStatus);
        }
        this.registrationStatus = FreelancerRegStatus.UNDER_REVIEW;
        this.updatedAt = Instant.now();
    }

    /**
     * Activates the organization (after verification).
     *
     * @throws IllegalStateException if not in VERIFIED status
     */
    public void activate() {
        if (this.registrationStatus != FreelancerRegStatus.VERIFIED) {
            throw new IllegalStateException(
                    "Cannot activate from status: " + this.registrationStatus);
        }
        this.registrationStatus = FreelancerRegStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * Suspends the organization (temporary — can appeal).
     *
     * @throws IllegalStateException if already BLACKLISTED
     */
    public void suspend() {
        if (this.registrationStatus == FreelancerRegStatus.BLACKLISTED) {
            throw new IllegalStateException("Cannot suspend a blacklisted organization");
        }
        this.registrationStatus = FreelancerRegStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    /**
     * Reactivates a suspended organization.
     *
     * @throws IllegalStateException if not in SUSPENDED status
     */
    public void unsuspend() {
        if (this.registrationStatus != FreelancerRegStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "Cannot unsuspend from status: " + this.registrationStatus);
        }
        this.registrationStatus = FreelancerRegStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * Permanently blacklists the organization (fraud, severe violations).
     */
    public void blacklist() {
        this.registrationStatus = FreelancerRegStatus.BLACKLISTED;
        this.updatedAt = Instant.now();
    }

    // ─── Capabilities & zones ─────────────────────────────────────────────────

    /**
     * Updates the operational capabilities of this organization.
     *
     * @param capabilities the new capabilities (must not be null)
     */
    public void updateCapabilities(FreelancerCapabilities capabilities) {
        if (capabilities == null) {
            throw new IllegalArgumentException("FreelancerCapabilities must not be null");
        }
        this.capabilities = capabilities;
        this.updatedAt = Instant.now();
    }

    /**
     * Adds a new operational zone.
     *
     * @param zone the zone to add (must not be null)
     */
    public void addOperationalZone(ServiceZone zone) {
        if (zone == null) {
            throw new IllegalArgumentException("ServiceZone must not be null");
        }
        this.operationalZones.add(zone);
        this.updatedAt = Instant.now();
    }

    /**
     * Replaces all operational zones with the given list.
     *
     * @param zones the new list of zones (must not be null)
     */
    public void setOperationalZones(List<ServiceZone> zones) {
        if (zones == null) {
            throw new IllegalArgumentException("Zones list must not be null");
        }
        this.operationalZones = new ArrayList<>(zones);
        this.updatedAt = Instant.now();
    }

    // ─── Sub-deliverer management ─────────────────────────────────────────────

    /**
     * Invites a new sub-deliverer by creating a PENDING_ACCEPTANCE association.
     *
     * @param delivererActorId actor UUID of the sub-deliverer to invite
     * @param commissionRate   offered commission rate (0.0–1.0)
     * @throws IllegalStateException    if the max sub-deliverer limit is reached
     * @throws IllegalArgumentException if the actor is already associated (active or pending)
     */
    public AssociatedDelivererRef inviteSubDeliverer(UUID delivererActorId,
                                                      BigDecimal commissionRate) {
        long activeOrPending = subDeliverers.stream()
                .filter(r -> r.status() == AssociationStatus.ACTIVE
                        || r.status() == AssociationStatus.PENDING_ACCEPTANCE)
                .count();
        if (activeOrPending >= MAX_SUB_DELIVERERS) {
            throw new IllegalStateException(
                    "FreelancerOrganization cannot have more than " + MAX_SUB_DELIVERERS
                            + " active/pending sub-deliverers");
        }
        boolean alreadyExists = subDeliverers.stream()
                .anyMatch(r -> r.delivererActorId().equals(delivererActorId)
                        && (r.status() == AssociationStatus.ACTIVE
                        || r.status() == AssociationStatus.PENDING_ACCEPTANCE));
        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "Actor " + delivererActorId + " is already active or pending in this org");
        }
        AssociatedDelivererRef ref = AssociatedDelivererRef.pending(delivererActorId, this.id, commissionRate);
        this.subDeliverers.add(ref);
        this.updatedAt = Instant.now();
        return ref;
    }

    /**
     * Accepts a sub-deliverer invitation (called when the sub-deliverer confirms).
     *
     * @param delivererActorId actor UUID of the sub-deliverer accepting the invite
     * @throws IllegalArgumentException if no pending invitation found for this actor
     */
    public void acceptSubDeliverer(UUID delivererActorId) {
        for (int i = 0; i < subDeliverers.size(); i++) {
            AssociatedDelivererRef ref = subDeliverers.get(i);
            if (ref.delivererActorId().equals(delivererActorId)
                    && ref.status() == AssociationStatus.PENDING_ACCEPTANCE) {
                subDeliverers.set(i, ref.activate());
                this.updatedAt = Instant.now();
                return;
            }
        }
        throw new IllegalArgumentException(
                "No pending invitation found for actor: " + delivererActorId);
    }

    /**
     * Revokes a sub-deliverer association (OWNER or admin action).
     *
     * @param delivererActorId actor UUID of the sub-deliverer to revoke
     * @throws IllegalArgumentException if no active/pending association found
     */
    public void revokeSubDeliverer(UUID delivererActorId) {
        for (int i = 0; i < subDeliverers.size(); i++) {
            AssociatedDelivererRef ref = subDeliverers.get(i);
            if (ref.delivererActorId().equals(delivererActorId)
                    && (ref.status() == AssociationStatus.ACTIVE
                    || ref.status() == AssociationStatus.PENDING_ACCEPTANCE)) {
                subDeliverers.set(i, ref.terminate());
                this.updatedAt = Instant.now();
                return;
            }
        }
        throw new IllegalArgumentException(
                "No active or pending association found for actor: " + delivererActorId);
    }

    // ─── Billing & trust ──────────────────────────────────────────────────────

    /**
     * Links an active billing policy to this organization.
     *
     * @param policyId UUID of the new active billing policy
     */
    public void assignBillingPolicy(UUID policyId) {
        this.billingProfile = this.billingProfile.withActivePolicy(policyId);
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the trade name displayed to clients.
     *
     * @param newTradeName the new commercial name (must not be blank)
     */
    public void updateTradeName(String newTradeName) {
        if (newTradeName == null || newTradeName.isBlank()) {
            throw new IllegalArgumentException("Trade name must not be blank");
        }
        this.tradeName = newTradeName;
        this.updatedAt = Instant.now();
    }

    /**
     * Records the blockchain DID issued by tnt-trust after VERIFIED status.
     *
     * @param did the blockchain Decentralized Identifier string
     */
    public void assignBlockchainDid(String did) {
        this.blockchainDid = did;
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the aggregated trust score (called by tnt-trust / tnt-actor-core events).
     *
     * @param newScore trust score in range [0.0, 5.0]
     */
    public void updateTrustScore(double newScore) {
        if (newScore < 0.0 || newScore > 5.0) {
            throw new IllegalArgumentException("Trust score must be in [0.0, 5.0], got: " + newScore);
        }
        this.trustScore = newScore;
        this.updatedAt = Instant.now();
    }

    // ─── Query helpers ────────────────────────────────────────────────────────

    /** @return {@code true} if this organization can accept missions */
    public boolean isOperational() {
        return registrationStatus == FreelancerRegStatus.ACTIVE
                || registrationStatus == FreelancerRegStatus.VERIFIED;
    }

    /**
     * Returns the count of currently active sub-deliverers.
     *
     * @return number of sub-deliverers with ACTIVE status
     */
    public long activeSubDelivererCount() {
        return subDeliverers.stream()
                .filter(r -> r.status() == AssociationStatus.ACTIVE)
                .count();
    }

    /**
     * Returns an unmodifiable view of the sub-deliverer list.
     *
     * @return unmodifiable list of {@link AssociatedDelivererRef}
     */
    public List<AssociatedDelivererRef> getSubDeliverers() {
        return Collections.unmodifiableList(subDeliverers);
    }

    /**
     * Returns an unmodifiable view of the operational zones list.
     *
     * @return unmodifiable list of {@link ServiceZone}
     */
    public List<ServiceZone> getOperationalZones() {
        return Collections.unmodifiableList(operationalZones);
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public OrganizationId getId()                        { return id; }
    public UUID getOrganizationId()                      { return organizationId; }
    public String getTenantId()                          { return tenantId; }
    public String getTradeName()                         { return tradeName; }
    public UUID getOwnerActorId()                        { return ownerActorId; }
    public FreelancerRegStatus getRegistrationStatus()   { return registrationStatus; }
    public KycLevel getKycLevel()                        { return kycLevel; }
    public FreelancerBillingProfile getBillingProfile()  { return billingProfile; }
    public double getTrustScore()                        { return trustScore; }
    public String getBlockchainDid()                     { return blockchainDid; }
    public FreelancerCapabilities getCapabilities()      { return capabilities; }
    public Instant getCreatedAt()                        { return createdAt; }
    public Instant getUpdatedAt()                        { return updatedAt; }
    public int getVersion()                              { return version; }
}
