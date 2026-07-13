package com.yowyob.tiibntick.core.marketback.domain.event;

import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.ReviewId;
import java.time.LocalDateTime;
import java.util.UUID;

/** Domain event — fired when a review is approved and published. @author MANFOUO Braun */
public record ProviderReviewPublishedEvent(
        ReviewId reviewId, MarketListingId listingId,
        double rating, UUID providerId, LocalDateTime occurredAt) implements MarketDomainEvent {
    @Override
    public String aggregateId() { return reviewId.toString(); }
}
