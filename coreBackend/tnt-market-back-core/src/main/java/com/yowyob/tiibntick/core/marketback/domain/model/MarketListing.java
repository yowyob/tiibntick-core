package com.yowyob.tiibntick.core.marketback.domain.model;

import com.yowyob.tiibntick.core.marketback.domain.event.MarketListingApprovedEvent;
import com.yowyob.tiibntick.core.marketback.domain.event.MarketListingPublishedEvent;
import com.yowyob.tiibntick.core.marketback.domain.event.MarketListingRejectedEvent;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — MarketListing (Provider Showcase on TiiBnTick Market).
 *
 * <p>A MarketListing represents the public storefront of a provider (agency,
 * freelancer or relay point) on the TiiBnTick Market discovery platform.
 * It aggregates the vitrine profile, coverage zone, SEO metadata, rating
 * statistics and moderation state.</p>
 *
 * @author MANFOUO Braun
 */
public class MarketListing {

    private final MarketListingId id;
    private final String tenantId;
    private final UUID providerId;
    private final ProviderType providerType;
    private final UUID organizationId;

    private ListingStatus status;
    private ListingVisibility visibility;
    private VitrineProfile vitrine;
    private CoverageZone coverageZone;
    private SeoMetadata seoMetadata;
    private String seoSlug;

    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime moderatedAt;
    private UUID moderatedBy;
    private String rejectionReason;

    private long viewCount;
    private long conversionCount;
    private double averageRating;
    private int totalReviews;

    private final LocalDateTime createdAt;
    private final List<Object> domainEvents = new ArrayList<>();

    // -------------------------------------------------------
    // Factory
    // -------------------------------------------------------

    /**
     * Creates a new MarketListing in DRAFT status.
     *
     * @param tenantId     Tenant identifier (multi-tenant context)
     * @param providerId   UUID of the provider (actor or organization)
     * @param providerType Type of provider (AGENCY, FREELANCER, RELAY_POINT)
     * @param vitrine      Initial vitrine profile
     * @return A new MarketListing instance
     */
    public static MarketListing create(
            String tenantId,
            UUID providerId,
            ProviderType providerType,
            VitrineProfile vitrine) {
        return new MarketListing(tenantId, providerId, null, providerType, vitrine);
    }

    private MarketListing(
            String tenantId,
            UUID providerId,
            UUID organizationId,
            ProviderType providerType,
            VitrineProfile vitrine) {
        this.id = MarketListingId.generate();
        this.tenantId = tenantId;
        this.providerId = providerId;
        this.organizationId = organizationId;
        this.providerType = providerType;
        this.vitrine = vitrine;
        this.status = ListingStatus.DRAFT;
        this.visibility = ListingVisibility.PUBLIC;
        this.viewCount = 0L;
        this.conversionCount = 0L;
        this.averageRating = 0.0;
        this.totalReviews = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // Private constructor for reconstitution from persistence
    private MarketListing() {
        this.id = null;
        this.tenantId = null;
        this.providerId = null;
        this.organizationId = null;
        this.providerType = null;
        this.createdAt = null;
    }

    /**
     * Reconstitutes a MarketListing aggregate from its persisted state.
     * Used exclusively by the persistence adapter layer.
     */
    public static MarketListing reconstitute(
            MarketListingId id,
            String tenantId,
            UUID providerId,
            UUID organizationId,
            ProviderType providerType,
            ListingStatus status,
            ListingVisibility visibility,
            VitrineProfile vitrine,
            CoverageZone coverageZone,
            SeoMetadata seoMetadata,
            String seoSlug,
            long viewCount,
            long conversionCount,
            double averageRating,
            int totalReviews,
            UUID moderatedBy,
            LocalDateTime moderatedAt,
            String rejectionReason,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        // Overwrite immutable id/createdAt via reflection-free trick: re-assign via local fields
        // Since id and createdAt are final we use a full-args private constructor instead
        return new MarketListing(id, tenantId, providerId, organizationId, providerType,
                status, visibility, vitrine, coverageZone, seoMetadata, seoSlug,
                viewCount, conversionCount, averageRating, totalReviews,
                moderatedBy, moderatedAt, rejectionReason, publishedAt, createdAt, updatedAt);
    }

    private MarketListing(
            MarketListingId id,
            String tenantId,
            UUID providerId,
            UUID organizationId,
            ProviderType providerType,
            ListingStatus status,
            ListingVisibility visibility,
            VitrineProfile vitrine,
            CoverageZone coverageZone,
            SeoMetadata seoMetadata,
            String seoSlug,
            long viewCount,
            long conversionCount,
            double averageRating,
            int totalReviews,
            UUID moderatedBy,
            LocalDateTime moderatedAt,
            String rejectionReason,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.providerId = providerId;
        this.organizationId = organizationId;
        this.providerType = providerType;
        this.status = status;
        this.visibility = visibility;
        this.vitrine = vitrine;
        this.coverageZone = coverageZone;
        this.seoMetadata = seoMetadata;
        this.seoSlug = seoSlug;
        this.viewCount = viewCount;
        this.conversionCount = conversionCount;
        this.averageRating = averageRating;
        this.totalReviews = totalReviews;
        this.moderatedBy = moderatedBy;
        this.moderatedAt = moderatedAt;
        this.rejectionReason = rejectionReason;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // -------------------------------------------------------
    // Business operations
    // -------------------------------------------------------

    /**
     * Submits the listing for moderation review.
     * Only allowed from DRAFT status.
     */
    public void submitForReview() {
        if (status != ListingStatus.DRAFT && status != ListingStatus.REJECTED) {
            throw new MarketDomainException(
                    "Cannot submit for review from status: " + status);
        }
        if (!vitrine.isComplete()) {
            throw new MarketDomainException(
                    "Vitrine profile is incomplete. Cannot submit for review.");
        }
        this.status = ListingStatus.PENDING_REVIEW;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Publishes the listing (approves moderation).
     * Fires MarketListingPublishedEvent.
     */
    public void publish() {
        if (status == ListingStatus.PUBLISHED) return;
        this.status = ListingStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.updatedAt = this.publishedAt;
        domainEvents.add(new MarketListingPublishedEvent(
                id, tenantId, providerId, providerType, publishedAt));
    }

    /**
     * Admin approves a listing pending review.
     */
    public void approve(UUID adminId) {
        if (status != ListingStatus.PENDING_REVIEW) {
            throw new MarketDomainException(
                    "Listing is not pending review. Current status: " + status);
        }
        this.moderatedBy = adminId;
        this.moderatedAt = LocalDateTime.now();
        publish();
        domainEvents.add(new MarketListingApprovedEvent(id, adminId, moderatedAt));
    }

    /**
     * Admin rejects a listing with a reason.
     */
    public void reject(UUID adminId, String reason) {
        if (status != ListingStatus.PENDING_REVIEW) {
            throw new MarketDomainException(
                    "Listing is not pending review. Current status: " + status);
        }
        this.status = ListingStatus.REJECTED;
        this.moderatedBy = adminId;
        this.moderatedAt = LocalDateTime.now();
        this.rejectionReason = reason;
        this.updatedAt = this.moderatedAt;
        domainEvents.add(new MarketListingRejectedEvent(id, adminId, reason, moderatedAt));
    }

    /**
     * Temporarily removes the listing from public visibility.
     */
    public void unpublish() {
        if (status != ListingStatus.PUBLISHED) {
            throw new MarketDomainException("Only published listings can be unpublished.");
        }
        this.status = ListingStatus.DRAFT;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Suspends the listing (admin action for policy violation).
     */
    public void suspend(String reason) {
        this.status = ListingStatus.SUSPENDED;
        this.rejectionReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Archives the listing permanently.
     */
    public void archive() {
        this.status = ListingStatus.ARCHIVED;
        this.updatedAt = LocalDateTime.now();
    }

    /** Updates the vitrine profile. Triggers back to PENDING_REVIEW if published. */
    public void updateProfile(VitrineProfile newProfile) {
        this.vitrine = newProfile;
        this.updatedAt = LocalDateTime.now();
        // If currently published, require new moderation on significant update
    }

    /** Updates the geographic coverage zone. */
    public void updateCoverage(CoverageZone zone) {
        this.coverageZone = zone;
        this.updatedAt = LocalDateTime.now();
    }

    /** Sets SEO metadata (title, description, slug). */
    public void setSeoMetadata(SeoMetadata seoMetadata, String slug) {
        this.seoMetadata = seoMetadata;
        this.seoSlug = slug;
        this.updatedAt = LocalDateTime.now();
    }

    /** Increments the view counter (analytics). */
    public void recordView() {
        this.viewCount++;
    }

    /** Increments the conversion counter (order placed via listing). */
    public void recordConversion() {
        this.conversionCount++;
    }

    /**
     * Updates the aggregate rating using an incremental formula.
     *
     * @param newRating New individual rating (1.0–5.0)
     */
    public void updateRating(double newRating) {
        this.averageRating = ((this.averageRating * this.totalReviews) + newRating)
                / (this.totalReviews + 1.0);
        this.totalReviews++;
    }

    /** Returns true if the listing is visible to the public. */
    public boolean isVisible() {
        return status == ListingStatus.PUBLISHED
                && visibility != ListingVisibility.PRIVATE;
    }

    // -------------------------------------------------------
    // Domain Events
    // -------------------------------------------------------

    /** Pulls and clears pending domain events. */
    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    // -------------------------------------------------------
    // Getters (no setters — immutable from outside)
    // -------------------------------------------------------

    public MarketListingId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getProviderId() { return providerId; }
    public ProviderType getProviderType() { return providerType; }
    public UUID getOrganizationId() { return organizationId; }
    public ListingStatus getStatus() { return status; }
    public ListingVisibility getVisibility() { return visibility; }
    public VitrineProfile getVitrine() { return vitrine; }
    public CoverageZone getCoverageZone() { return coverageZone; }
    public SeoMetadata getSeoMetadata() { return seoMetadata; }
    public String getSeoSlug() { return seoSlug; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getModeratedAt() { return moderatedAt; }
    public UUID getModeratedBy() { return moderatedBy; }
    public String getRejectionReason() { return rejectionReason; }
    public long getViewCount() { return viewCount; }
    public long getConversionCount() { return conversionCount; }
    public double getAverageRating() { return averageRating; }
    public int getTotalReviews() { return totalReviews; }
}
