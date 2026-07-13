package com.yowyob.tiibntick.core.marketback.domain.model;

import com.yowyob.tiibntick.core.marketback.domain.event.ProviderReviewPublishedEvent;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — ProviderReview (Client review of a provider).
 * @author MANFOUO Braun
 */
public class ProviderReview {

    private final ReviewId id;
    private final String tenantId;
    private final MarketListingId listingId;
    private final MarketOrderId orderId;
    private final UUID clientId;
    private final UUID providerId;

    private Rating rating;
    private String comment;
    private List<ReviewTag> tags;
    private ReviewStatus status;
    private UUID moderatedBy;
    private LocalDateTime moderatedAt;
    private String rejectionReason;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<Object> domainEvents = new ArrayList<>();

    public static ProviderReview create(
            String tenantId, MarketOrderId orderId, UUID clientId,
            MarketListingId listingId, UUID providerId,
            Rating rating, String comment, List<ReviewTag> tags) {
        return new ProviderReview(tenantId, orderId, clientId, listingId, providerId, rating, comment, tags);
    }

    private ProviderReview(String tenantId, MarketOrderId orderId, UUID clientId,
            MarketListingId listingId, UUID providerId,
            Rating rating, String comment, List<ReviewTag> tags) {
        this.id = ReviewId.generate();
        this.tenantId = tenantId;
        this.listingId = listingId;
        this.orderId = orderId;
        this.clientId = clientId;
        this.providerId = providerId;
        this.rating = rating;
        this.comment = comment;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        this.status = ReviewStatus.PENDING_MODERATION;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    ProviderReview() {
        this.id = null; this.tenantId = null; this.listingId = null;
        this.orderId = null; this.clientId = null; this.providerId = null; this.createdAt = null;
    }

    /** Reconstitutes a ProviderReview from its persisted state. */
    public static ProviderReview reconstitute(
            ReviewId id, String tenantId, UUID clientId, UUID providerId,
            MarketListingId listingId, UUID orderIdRaw,
            Rating rating, String comment, List<ReviewTag> tags,
            ReviewStatus status, UUID moderatedBy, LocalDateTime moderatedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new ProviderReview(id, tenantId, clientId, providerId, listingId,
                orderIdRaw != null ? new MarketOrderId(orderIdRaw) : null,
                rating, comment, tags, status, moderatedBy, moderatedAt, createdAt, updatedAt);
    }

    private ProviderReview(
            ReviewId id, String tenantId, UUID clientId, UUID providerId,
            MarketListingId listingId, MarketOrderId orderId,
            Rating rating, String comment, List<ReviewTag> tags,
            ReviewStatus status, UUID moderatedBy, LocalDateTime moderatedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.providerId = providerId;
        this.listingId = listingId;
        this.orderId = orderId;
        this.rating = rating;
        this.comment = comment;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        this.status = status;
        this.moderatedBy = moderatedBy;
        this.moderatedAt = moderatedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void approve(UUID adminId) {
        this.moderatedBy = adminId;
        this.moderatedAt = LocalDateTime.now();
        this.status = ReviewStatus.PUBLISHED;
        this.updatedAt = this.moderatedAt;
        domainEvents.add(new ProviderReviewPublishedEvent(
                id, listingId, rating.average(), providerId, moderatedAt));
    }

    public void reject(UUID adminId, String reason) {
        this.moderatedBy = adminId;
        this.moderatedAt = LocalDateTime.now();
        this.status = ReviewStatus.REJECTED;
        this.rejectionReason = reason;
        this.updatedAt = this.moderatedAt;
    }

    public void flag(String reason) {
        this.status = ReviewStatus.FLAGGED;
        this.rejectionReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    public void update(Rating newRating, String newComment) {
        if (!rating.isValid()) throw new MarketDomainException("Invalid rating values.");
        this.rating = newRating;
        this.comment = newComment;
        this.status = ReviewStatus.PENDING_MODERATION;
        this.updatedAt = LocalDateTime.now();
    }

    public List<Object> pullDomainEvents() {
        List<Object> evts = List.copyOf(domainEvents);
        domainEvents.clear();
        return evts;
    }

    public ReviewId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public MarketListingId getListingId() { return listingId; }
    public MarketOrderId getOrderId() { return orderId; }
    public UUID getClientId() { return clientId; }
    public UUID getProviderId() { return providerId; }
    public Rating getRating() { return rating; }
    public String getComment() { return comment; }
    public List<ReviewTag> getTags() { return List.copyOf(tags); }
    public ReviewStatus getStatus() { return status; }
    public UUID getModeratedBy() { return moderatedBy; }
    public LocalDateTime getModeratedAt() { return moderatedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
