package com.yowyob.tiibntick.core.tp.domain.model;

import com.yowyob.tiibntick.core.tp.domain.event.ClientProfileRegisteredEvent;
import com.yowyob.tiibntick.core.tp.domain.model.enums.KycStatus;
import com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTier;
import com.yowyob.tiibntick.core.tp.domain.model.enums.TntThirdPartyRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate Root: TiiBnTick Client Profile.
 *
 * <p>TiiBnTick-specific extension for third parties registered in the platform.
 * This entity references the Yowyob Kernel's ThirdParty (RT-comops-tp-core) by its
 * {@link UUID} key ({@code thirdPartyId}), adding logistics-domain-specific attributes:
 * KYC status, loyalty program participation, phone masking for relay-point anonymity,
 * rating aggregation, and TiiBnTick roles.</p>
 *
 * <p><strong>Kernel integration rule:</strong> No Java inheritance from any Kernel class.
 * The {@code thirdPartyId} field is the logical integration key validated via
 * {@link com.yowyob.tiibntick.core.tp.application.port.out.KernelThirdPartyPort}
 * before any profile creation.</p>
 *
 * <p>Immutable. Domain events are collected via {@link #collectAndClearEvents()} and
 * dispatched after persistence.</p>
 *
 * @author MANFOUO Braun
 */
public final class TntClientProfile {

    private final UUID id;
    private final UUID tenantId;

    /**
     * Kernel integration key.
     * References the ThirdParty entity in RT-comops-tp-core.
     * Validated via {@code KernelThirdPartyPort} before profile creation.
     * Must not be null.
     */
    private final UUID thirdPartyId;

    /** TiiBnTick-specific roles (SENDER, RECIPIENT, DELIVERER, etc.). */
    private final Set<TntThirdPartyRole> tntRoles;

    /** Simplified KYC status adapted to the informal Cameroonian context. */
    private final KycStatus kycStatus;

    /** Phone alias (masked phone for relay-point anonymity). */
    private final String phoneAlias;

    /** Whether the phone is currently masked. */
    private final boolean phoneMasked;

    /** Cumulative average rating (1.0 – 5.0), null if not yet rated. */
    private final Double averageRating;

    /** Total number of ratings received. */
    private final int ratingCount;

    /** Total successful deliveries (sent or received). */
    private final int totalDeliveries;

    /** Preferred locale (e.g., "fr", "en", "ful"). */
    private final String preferredLocale;

    /** ISO 4217 preferred currency (e.g., "XAF", "NGN"). */
    private final String preferredCurrency;

    /** Loyalty tier derived from the linked LoyaltyAccount. */
    private final LoyaltyTier loyaltyTier;

    /**
     * Map of provider types to provider IDs for this third party.
     * Example: {@code {"AGENCY": "AGY-xxx", "FREELANCER_ORG": "FRL-yyy"}}.
     *
     * <p>A client can be known to both an Agency and a FreelancerOrg simultaneously.
     * Used by tnt-billing-pricing's DSL to evaluate {@code isRecurringClient} per provider.
     *
     * <p> — Added to support the FreelancerOrganization multi-provider model.
     * References tnt-organization-core UUIDs — pure integration keys (no join).
     */
    private final Map<String, String> providerLinks;

    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    /** Pending domain events — dispatched after persistence. */
    private final List<Object> domainEvents;

    private TntClientProfile(
            UUID id,
            UUID tenantId,
            UUID thirdPartyId,
            Set<TntThirdPartyRole> tntRoles,
            KycStatus kycStatus,
            String phoneAlias,
            boolean phoneMasked,
            Double averageRating,
            int ratingCount,
            int totalDeliveries,
            String preferredLocale,
            String preferredCurrency,
            LoyaltyTier loyaltyTier,
            Map<String, String> providerLinks,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            List<Object> domainEvents) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.thirdPartyId = Objects.requireNonNull(thirdPartyId,
                "thirdPartyId (Kernel integration key) is required");
        this.tntRoles = tntRoles == null || tntRoles.isEmpty()
                ? Set.of(TntThirdPartyRole.SENDER)
                : Collections.unmodifiableSet(tntRoles);
        this.kycStatus = kycStatus != null ? kycStatus : KycStatus.NOT_SUBMITTED;
        this.phoneAlias = phoneAlias;
        this.phoneMasked = phoneMasked;
        this.averageRating = averageRating;
        this.ratingCount = Math.max(0, ratingCount);
        this.totalDeliveries = Math.max(0, totalDeliveries);
        this.preferredLocale = preferredLocale != null ? preferredLocale : "fr";
        this.preferredCurrency = preferredCurrency != null ? preferredCurrency : "XAF";
        this.loyaltyTier = loyaltyTier != null ? loyaltyTier : LoyaltyTier.BRONZE;
        this.providerLinks = providerLinks != null
                ? java.util.Collections.unmodifiableMap(new HashMap<>(providerLinks))
                : Map.of();
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        this.domainEvents = domainEvents != null ? new ArrayList<>(domainEvents) : new ArrayList<>();
    }

    // ─── Factory methods ─────────────────────────────────────────────────────

    /**
     * Creates a new {@link TntClientProfile} and emits a {@link ClientProfileRegisteredEvent}.
     *
     * <p><strong>Precondition:</strong> the {@code thirdPartyId} must exist and be active
     * in RT-comops-tp-core (verified upstream via {@code KernelThirdPartyPort}).
     *
     * @param tenantId          the tenant owning this profile
     * @param thirdPartyId      the Kernel ThirdParty reference UUID (must be active)
     * @param roles             the initial TiiBnTick roles (must not be empty)
     * @param preferredLocale   the preferred locale code (null → defaults to "fr")
     * @param preferredCurrency the preferred ISO currency code (null → defaults to "XAF")
     * @return a new TntClientProfile with one domain event
     */
    public static TntClientProfile create(
            UUID tenantId,
            UUID thirdPartyId,
            Set<TntThirdPartyRole> roles,
            String preferredLocale,
            String preferredCurrency) {

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        TntClientProfile profile = new TntClientProfile(
                id, tenantId, thirdPartyId, roles,
                KycStatus.NOT_SUBMITTED, null, false,
                null, 0, 0,
                preferredLocale, preferredCurrency,
                LoyaltyTier.BRONZE, Map.of(), true, now, now, new ArrayList<>());

        profile.domainEvents.add(new ClientProfileRegisteredEvent(id, tenantId, thirdPartyId, roles, now));
        return profile;
    }

    /**
     * Reconstitutes a {@link TntClientProfile} from persistence data.
     *
     * <p>Unlike {@link #create(UUID, UUID, Set, String, String)}, this method does
     * <strong>not</strong> generate a new UUID or emit domain events. It is used
     * exclusively by repository adapters to restore the aggregate from the database.
     *
     * @param id                the persisted profile UUID
     * @param tenantId          the tenant UUID
     * @param thirdPartyId      the Kernel ThirdParty reference UUID
     * @param tntRoles          the persisted TNT roles
     * @param kycStatus         the persisted KYC status
     * @param phoneAlias        the persisted phone alias (nullable)
     * @param phoneMasked       the persisted phone masking flag
     * @param averageRating     the persisted average rating (nullable)
     * @param ratingCount       the persisted rating count
     * @param totalDeliveries   the persisted delivery count
     * @param preferredLocale   the persisted preferred locale
     * @param preferredCurrency the persisted preferred currency
     * @param loyaltyTier       the persisted loyalty tier
     * @param active            the persisted active flag
     * @param createdAt         the persisted creation timestamp
     * @param updatedAt         the persisted update timestamp
     * @return the reconstituted {@link TntClientProfile}
     */
    public static TntClientProfile reconstitute(
            UUID id,
            UUID tenantId,
            UUID thirdPartyId,
            Set<TntThirdPartyRole> tntRoles,
            KycStatus kycStatus,
            String phoneAlias,
            boolean phoneMasked,
            Double averageRating,
            int ratingCount,
            int totalDeliveries,
            String preferredLocale,
            String preferredCurrency,
            LoyaltyTier loyaltyTier,
            Map<String, String> providerLinks,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        return new TntClientProfile(
                id, tenantId, thirdPartyId, tntRoles,
                kycStatus, phoneAlias, phoneMasked,
                averageRating, ratingCount, totalDeliveries,
                preferredLocale, preferredCurrency, loyaltyTier,
                providerLinks, active, createdAt, updatedAt, new ArrayList<>());
    }

    // ─── Business methods ────────────────────────────────────────────────────

    /**
     * Updates the KYC status of the profile.
     *
     * @param newStatus the new KYC status (must not be null)
     * @return updated profile copy
     */
    public TntClientProfile updateKycStatus(KycStatus newStatus) {
        Objects.requireNonNull(newStatus, "newStatus is required");
        return new TntClientProfile(
                id, tenantId, thirdPartyId, tntRoles,
                newStatus, phoneAlias, phoneMasked,
                averageRating, ratingCount, totalDeliveries,
                preferredLocale, preferredCurrency, loyaltyTier,
                providerLinks, active, createdAt, Instant.now(), new ArrayList<>());
    }

    /**
     * Applies a new rating and recalculates the running average (rounded to 1 decimal).
     *
     * @param newRating rating value between 1.0 and 5.0 inclusive
     * @return updated profile copy with recalculated average
     * @throws IllegalArgumentException if the rating is outside [1.0, 5.0]
     */
    public TntClientProfile applyRating(double newRating) {
        if (newRating < 1.0 || newRating > 5.0) {
            throw new IllegalArgumentException("Rating must be between 1.0 and 5.0, got: " + newRating);
        }
        double currentTotal = averageRating != null ? averageRating * ratingCount : 0.0;
        int newCount = ratingCount + 1;
        double newAverage = (currentTotal + newRating) / newCount;

        return new TntClientProfile(
                id, tenantId, thirdPartyId, tntRoles,
                kycStatus, phoneAlias, phoneMasked,
                Math.round(newAverage * 10.0) / 10.0, newCount, totalDeliveries,
                preferredLocale, preferredCurrency, loyaltyTier,
                providerLinks, active, createdAt, Instant.now(), new ArrayList<>());
    }

    /**
     * Assigns a phone alias for anonymized relay-point interactions.
     *
     * @param alias the generated phone alias (must not be null)
     * @return updated profile copy with phone masked
     */
    public TntClientProfile assignPhoneAlias(String alias) {
        Objects.requireNonNull(alias, "alias is required");
        return new TntClientProfile(
                id, tenantId, thirdPartyId, tntRoles,
                kycStatus, alias, true,
                averageRating, ratingCount, totalDeliveries,
                preferredLocale, preferredCurrency, loyaltyTier,
                providerLinks, active, createdAt, Instant.now(), new ArrayList<>());
    }

    /**
     * Removes the phone alias (reveals real phone).
     *
     * @return updated profile copy with phone unmasked
     */
    public TntClientProfile removePhoneAlias() {
        return new TntClientProfile(
                id, tenantId, thirdPartyId, tntRoles,
                kycStatus, null, false,
                averageRating, ratingCount, totalDeliveries,
                preferredLocale, preferredCurrency, loyaltyTier,
                providerLinks, active, createdAt, Instant.now(), new ArrayList<>());
    }

    /**
     * Updates the loyalty tier derived from the current points balance.
     *
     * @param currentPoints the current loyalty points total
     * @return updated profile with recalculated tier
     */
    public TntClientProfile updateLoyaltyTier(int currentPoints) {
        LoyaltyTier newTier = LoyaltyTier.fromPoints(currentPoints);
        return new TntClientProfile(
                id, tenantId, thirdPartyId, tntRoles,
                kycStatus, phoneAlias, phoneMasked,
                averageRating, ratingCount, totalDeliveries,
                preferredLocale, preferredCurrency, newTier,
                providerLinks, active, createdAt, Instant.now(), new ArrayList<>());
    }

    /**
     * Increments the successful deliveries counter by one.
     *
     * @return updated profile copy
     */
    public TntClientProfile incrementDeliveries() {
        return new TntClientProfile(
                id, tenantId, thirdPartyId, tntRoles,
                kycStatus, phoneAlias, phoneMasked,
                averageRating, ratingCount, totalDeliveries + 1,
                preferredLocale, preferredCurrency, loyaltyTier,
                providerLinks, active, createdAt, Instant.now(), new ArrayList<>());
    }
    /**
     * Links this profile to a FreelancerOrganization.
     * Sets the provider link and grants the FREELANCER_ORG_CLIENT role.
     *
     * @param freelancerOrgId UUID of the FreelancerOrg
     * @return updated profile copy
     */
    public TntClientProfile linkToFreelancerOrg(String freelancerOrgId) {
        Objects.requireNonNull(freelancerOrgId, "freelancerOrgId is required");

        Map<String, String> updatedLinks = new HashMap<>(providerLinks);
        updatedLinks.put("FREELANCER_ORG", freelancerOrgId);

        Set<TntThirdPartyRole> updatedRoles = new HashSet<>(tntRoles);
        updatedRoles.add(TntThirdPartyRole.FREELANCER_ORG_CLIENT);

        return new TntClientProfile(
                id, tenantId, thirdPartyId, updatedRoles,
                kycStatus, phoneAlias, phoneMasked,
                averageRating, ratingCount, totalDeliveries,
                preferredLocale, preferredCurrency, loyaltyTier,
                updatedLinks, active, createdAt, Instant.now(), new ArrayList<>(domainEvents));
    }

    /**
     * Unlinks this profile from a FreelancerOrganization.
     * Removes the provider link and revokes the FREELANCER_ORG_CLIENT role.
     *
     * @param freelancerOrgId UUID of the FreelancerOrg to unlink
     * @return updated profile copy
     */
    public TntClientProfile unlinkFromFreelancerOrg(String freelancerOrgId) {
        Map<String, String> updatedLinks = new HashMap<>(providerLinks);
        updatedLinks.remove("FREELANCER_ORG");

        Set<TntThirdPartyRole> updatedRoles = new HashSet<>(tntRoles);
        updatedRoles.remove(TntThirdPartyRole.FREELANCER_ORG_CLIENT);

        return new TntClientProfile(
                id, tenantId, thirdPartyId, updatedRoles,
                kycStatus, phoneAlias, phoneMasked,
                averageRating, ratingCount, totalDeliveries,
                preferredLocale, preferredCurrency, loyaltyTier,
                updatedLinks, active, createdAt, Instant.now(), new ArrayList<>(domainEvents));
    }

    /**
     * Deactivates the profile (soft delete — data retained).
     *
     * @return updated profile copy with {@code active = false}
     */
    public TntClientProfile deactivate() {
        return new TntClientProfile(
                id, tenantId, thirdPartyId, tntRoles,
                kycStatus, phoneAlias, phoneMasked,
                averageRating, ratingCount, totalDeliveries,
                preferredLocale, preferredCurrency, loyaltyTier,
                providerLinks, false, createdAt, Instant.now(), new ArrayList<>());
    }

    /**
     * Returns whether the profile passed KYC verification.
     *
     * @return {@code true} if {@link KycStatus#APPROVED}
     */
    public boolean isKycVerified() {
        return KycStatus.APPROVED.equals(kycStatus);
    }

    /**
     * Returns whether the third party has a specific TiiBnTick role.
     *
     * @param role the role to check
     * @return {@code true} if the role is assigned
     */
    public boolean hasRole(TntThirdPartyRole role) {
        return tntRoles.contains(role);
    }

    /**
     * Returns and clears pending domain events.
     * Call this method <strong>after</strong> persisting the aggregate to publish events.
     *
     * @return the list of pending domain events (caller owns the list)
     */
    public List<Object> collectAndClearEvents() {
        List<Object> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return events;
    }

    // ─── Accessors ───────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getThirdPartyId() { return thirdPartyId; }
    public Set<TntThirdPartyRole> getTntRoles() { return tntRoles; }
    public KycStatus getKycStatus() { return kycStatus; }
    public String getPhoneAlias() { return phoneAlias; }
    public boolean isPhoneMasked() { return phoneMasked; }
    public Double getAverageRating() { return averageRating; }
    public int getRatingCount() { return ratingCount; }
    public int getTotalDeliveries() { return totalDeliveries; }
    public String getPreferredLocale() { return preferredLocale; }
    public String getPreferredCurrency() { return preferredCurrency; }
    public LoyaltyTier getLoyaltyTier() { return loyaltyTier; }
    public boolean isActive() { return active; }
    public Map<String, String> getProviderLinks() { return providerLinks; }

    /** Returns true if this profile is linked to a FreelancerOrganization. */
    public boolean isLinkedToFreelancerOrg() {
        return providerLinks.containsKey("FREELANCER_ORG");
    }

    /** Returns the FreelancerOrg UUID this profile is linked to, or null if not linked. */
    public String getLinkedFreelancerOrgId() {
        return providerLinks.get("FREELANCER_ORG");
    }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<Object> getDomainEvents() { return Collections.unmodifiableList(domainEvents); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TntClientProfile that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TntClientProfile{id=" + id + ", thirdPartyId=" + thirdPartyId
                + ", kycStatus=" + kycStatus + ", loyaltyTier=" + loyaltyTier + "}";
    }
}
