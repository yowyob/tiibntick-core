package com.yowyob.tiibntick.core.marketback.domain.event;

import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.ProviderType;
import java.time.LocalDateTime;
import java.util.UUID;

/** Domain event — fired when a MarketListing is published. @author MANFOUO Braun */
public record MarketListingPublishedEvent(
        MarketListingId listingId,
        String tenantId,
        UUID providerId,
        ProviderType providerType,
        LocalDateTime occurredAt) implements MarketDomainEvent {
    @Override
    public String aggregateId() { return listingId.toString(); }
}
