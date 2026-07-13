package com.yowyob.tiibntick.core.marketback.domain.event;

import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.QuoteRequestId;
import java.time.LocalDateTime;
import java.util.UUID;

/** Domain event — fired when a client creates a quote request. @author MANFOUO Braun */
public record QuoteRequestCreatedEvent(
        QuoteRequestId quoteRequestId, MarketListingId listingId,
        UUID clientId, UUID providerId, LocalDateTime occurredAt) implements MarketDomainEvent {
    @Override
    public String aggregateId() { return quoteRequestId.toString(); }
}
