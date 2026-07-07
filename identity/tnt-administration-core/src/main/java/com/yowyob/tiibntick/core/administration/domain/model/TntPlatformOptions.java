package com.yowyob.tiibntick.core.administration.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TiiBnTick platform-specific options, extending the kernel's AdministrativePlatformOptions.
 * These options control TiiBnTick-specific features per tenant.
 * Author: MANFOUO Braun
 */
public final class TntPlatformOptions {

    private final UUID id;
    private final UUID tenantId;

    // ── Blockchain / Trust ─────────────────────────────────────────────────────
    /** Whether blockchain trust features (TiiBnTick Trust) are enabled for this tenant. */
    private final boolean blockchainEnabled;
    /** Whether smart-contract-based auto-dispute resolution is enabled. */
    private final boolean smartDisputeResolutionEnabled;
    /** Blockchain network to use: "PUBLIC_LITE" | "PRIVATE" | "HYBRID". */
    private final String blockchainNetwork;

    // ── Freelancer mode ────────────────────────────────────────────────────────
    /** Whether freelancer (independent courier) mode is enabled. */
    private final boolean freelancerModeEnabled;
    /** Whether freelancers require manual approval by an admin. */
    private final boolean requireFreelancerApproval;
    /** Maximum concurrent missions a freelancer can have. */
    private final int maxFreelancerConcurrentMissions;

    // ── Point Relais mode ──────────────────────────────────────────────────────
    /** Whether relay points (TiiBnTick Point) are enabled. */
    private final boolean pointRelaisModeEnabled;
    /** Maximum storage duration (hours) at a relay point before a parcel is returned. */
    private final int relayPointMaxStorageHours;

    // ── Announcement / TiiBnPick ───────────────────────────────────────────────
    /** Whether the delivery announcement marketplace (TiiBnPick) is enabled. */
    private final boolean announcementMarketplaceEnabled;
    /** Maximum number of concurrent responses a courier can make to announcements. */
    private final int maxCourierAnnouncementResponses;

    // ── Pricing / Billing ──────────────────────────────────────────────────────
    /** Default TVA rate for this tenant (e.g., 19.25 for Cameroon). */
    private final java.math.BigDecimal tvaRate;
    /** Default currency code (XAF for CEMAC zone). */
    private final String defaultCurrency;

    // ── Dispute ───────────────────────────────────────────────────────────────
    /** Whether dispute management is enabled. */
    private final boolean disputeManagementEnabled;
    /** Number of days after delivery a client can file a dispute. */
    private final int disputeFilingWindowDays;

    // ── FreelancerOrg mode () ──────────────────────────────────────────────
    /**
     * Whether FreelancerOrganization mode is enabled (distinct org entity vs. individual freelancer).
     * When true, freelancers can create a FreelancerOrg, manage a fleet, define own billing policy.
     */
    private final boolean freelancerOrgModeEnabled;
    /**
     * Maximum number of vehicles allowed per FreelancerOrg fleet.
     * Default: 3 (benskin operators rarely have more than 3 vehicles).
     */
    private final int maxFreelancerOrgFleetSize;

    // ── Billing Templates () ──────────────────────────────────────────────
    /**
     * Whether the billing templates feature is enabled for this tenant.
     * When true, actors can create billing policies from pre-defined templates.
     */
    private final boolean billingTemplatesEnabled;
    /**
     * Maximum DSL access level for non-admin actors: FULL | SIMPLIFIED | NONE.
     * FULL = Agency, Admin. SIMPLIFIED = Freelancer, PointOperator. NONE = Clients.
     */
    private final String maxBillingTemplateDslLevel;

    private final Instant createdAt;
    private final Instant updatedAt;

    private TntPlatformOptions(UUID id, UUID tenantId, boolean blockchainEnabled,
                                boolean smartDisputeResolutionEnabled, String blockchainNetwork,
                                boolean freelancerModeEnabled, boolean requireFreelancerApproval,
                                int maxFreelancerConcurrentMissions, boolean pointRelaisModeEnabled,
                                int relayPointMaxStorageHours, boolean announcementMarketplaceEnabled,
                                int maxCourierAnnouncementResponses, java.math.BigDecimal tvaRate,
                                String defaultCurrency, boolean disputeManagementEnabled,
                                int disputeFilingWindowDays,
                                boolean freelancerOrgModeEnabled, int maxFreelancerOrgFleetSize,
                                boolean billingTemplatesEnabled, String maxBillingTemplateDslLevel,
                                Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.blockchainEnabled = blockchainEnabled;
        this.smartDisputeResolutionEnabled = smartDisputeResolutionEnabled;
        this.blockchainNetwork = blockchainNetwork;
        this.freelancerModeEnabled = freelancerModeEnabled;
        this.requireFreelancerApproval = requireFreelancerApproval;
        this.maxFreelancerConcurrentMissions = maxFreelancerConcurrentMissions;
        this.pointRelaisModeEnabled = pointRelaisModeEnabled;
        this.relayPointMaxStorageHours = relayPointMaxStorageHours;
        this.announcementMarketplaceEnabled = announcementMarketplaceEnabled;
        this.maxCourierAnnouncementResponses = maxCourierAnnouncementResponses;
        this.tvaRate = tvaRate;
        this.defaultCurrency = defaultCurrency;
        this.disputeManagementEnabled = disputeManagementEnabled;
        this.disputeFilingWindowDays = disputeFilingWindowDays;
        this.freelancerOrgModeEnabled = freelancerOrgModeEnabled;
        this.maxFreelancerOrgFleetSize = maxFreelancerOrgFleetSize > 0 ? maxFreelancerOrgFleetSize : 3;
        this.billingTemplatesEnabled = billingTemplatesEnabled;
        this.maxBillingTemplateDslLevel = maxBillingTemplateDslLevel != null ? maxBillingTemplateDslLevel : "SIMPLIFIED";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static TntPlatformOptions defaults(UUID tenantId) {
        Instant now = Instant.now();
        return new TntPlatformOptions(UUID.randomUUID(), tenantId,
                true, false, "PUBLIC_LITE",
                true, true, 3,
                true, 72,
                true, 5,
                new java.math.BigDecimal("19.25"), "XAF",
                true, 7,
                true, 3,
                true, "SIMPLIFIED",
                now, now);
    }

    public static TntPlatformOptions rehydrate(UUID id, UUID tenantId,
                                                boolean blockchainEnabled, boolean smartDisputeResolutionEnabled,
                                                String blockchainNetwork, boolean freelancerModeEnabled,
                                                boolean requireFreelancerApproval, int maxFreelancerConcurrentMissions,
                                                boolean pointRelaisModeEnabled, int relayPointMaxStorageHours,
                                                boolean announcementMarketplaceEnabled, int maxCourierAnnouncementResponses,
                                                java.math.BigDecimal tvaRate, String defaultCurrency,
                                                boolean disputeManagementEnabled, int disputeFilingWindowDays,
                                                Instant createdAt, Instant updatedAt) {
        return rehydrateFull(id, tenantId, blockchainEnabled, smartDisputeResolutionEnabled,
                blockchainNetwork, freelancerModeEnabled, requireFreelancerApproval,
                maxFreelancerConcurrentMissions, pointRelaisModeEnabled, relayPointMaxStorageHours,
                announcementMarketplaceEnabled, maxCourierAnnouncementResponses, tvaRate, defaultCurrency,
                disputeManagementEnabled, disputeFilingWindowDays,
                true, 3, true, "SIMPLIFIED",
                createdAt, updatedAt);
    }

    /**
     * Full rehydration factory including  FreelancerOrg and billing template fields.
     */
    public static TntPlatformOptions rehydrateFull(UUID id, UUID tenantId,
                                                boolean blockchainEnabled, boolean smartDisputeResolutionEnabled,
                                                String blockchainNetwork, boolean freelancerModeEnabled,
                                                boolean requireFreelancerApproval, int maxFreelancerConcurrentMissions,
                                                boolean pointRelaisModeEnabled, int relayPointMaxStorageHours,
                                                boolean announcementMarketplaceEnabled, int maxCourierAnnouncementResponses,
                                                java.math.BigDecimal tvaRate, String defaultCurrency,
                                                boolean disputeManagementEnabled, int disputeFilingWindowDays,
                                                boolean freelancerOrgModeEnabled, int maxFreelancerOrgFleetSize,
                                                boolean billingTemplatesEnabled, String maxBillingTemplateDslLevel,
                                                Instant createdAt, Instant updatedAt) {
        return new TntPlatformOptions(id, tenantId, blockchainEnabled, smartDisputeResolutionEnabled,
                blockchainNetwork, freelancerModeEnabled, requireFreelancerApproval,
                maxFreelancerConcurrentMissions, pointRelaisModeEnabled, relayPointMaxStorageHours,
                announcementMarketplaceEnabled, maxCourierAnnouncementResponses, tvaRate, defaultCurrency,
                disputeManagementEnabled, disputeFilingWindowDays,
                freelancerOrgModeEnabled, maxFreelancerOrgFleetSize,
                billingTemplatesEnabled, maxBillingTemplateDslLevel,
                createdAt, updatedAt);
    }

    public TntPlatformOptions withBlockchain(boolean enabled, String network) {
        return new TntPlatformOptions(id, tenantId, enabled, smartDisputeResolutionEnabled,
                network != null ? network : blockchainNetwork, freelancerModeEnabled, requireFreelancerApproval,
                maxFreelancerConcurrentMissions, pointRelaisModeEnabled, relayPointMaxStorageHours,
                announcementMarketplaceEnabled, maxCourierAnnouncementResponses, tvaRate, defaultCurrency,
                disputeManagementEnabled, disputeFilingWindowDays,
                freelancerOrgModeEnabled, maxFreelancerOrgFleetSize,
                billingTemplatesEnabled, maxBillingTemplateDslLevel,
                createdAt, Instant.now());
    }

    public TntPlatformOptions withFreelancerMode(boolean enabled, boolean requireApproval, int maxMissions) {
        return new TntPlatformOptions(id, tenantId, blockchainEnabled, smartDisputeResolutionEnabled,
                blockchainNetwork, enabled, requireApproval, maxMissions, pointRelaisModeEnabled,
                relayPointMaxStorageHours, announcementMarketplaceEnabled, maxCourierAnnouncementResponses,
                tvaRate, defaultCurrency, disputeManagementEnabled, disputeFilingWindowDays,
                freelancerOrgModeEnabled, maxFreelancerOrgFleetSize,
                billingTemplatesEnabled, maxBillingTemplateDslLevel,
                createdAt, Instant.now());
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public boolean isBlockchainEnabled() { return blockchainEnabled; }
    public boolean isSmartDisputeResolutionEnabled() { return smartDisputeResolutionEnabled; }
    public String getBlockchainNetwork() { return blockchainNetwork; }
    public boolean isFreelancerModeEnabled() { return freelancerModeEnabled; }
    public boolean isRequireFreelancerApproval() { return requireFreelancerApproval; }
    public int getMaxFreelancerConcurrentMissions() { return maxFreelancerConcurrentMissions; }
    public boolean isPointRelaisModeEnabled() { return pointRelaisModeEnabled; }
    public int getRelayPointMaxStorageHours() { return relayPointMaxStorageHours; }
    public boolean isAnnouncementMarketplaceEnabled() { return announcementMarketplaceEnabled; }
    public int getMaxCourierAnnouncementResponses() { return maxCourierAnnouncementResponses; }
    public java.math.BigDecimal getTvaRate() { return tvaRate; }
    public String getDefaultCurrency() { return defaultCurrency; }
    public boolean isDisputeManagementEnabled() { return disputeManagementEnabled; }
    public int getDisputeFilingWindowDays() { return disputeFilingWindowDays; }
    public boolean isFreelancerOrgModeEnabled() { return freelancerOrgModeEnabled; }
    public int getMaxFreelancerOrgFleetSize() { return maxFreelancerOrgFleetSize; }
    public boolean isBillingTemplatesEnabled() { return billingTemplatesEnabled; }
    public String getMaxBillingTemplateDslLevel() { return maxBillingTemplateDslLevel; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
