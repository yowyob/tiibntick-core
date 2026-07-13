package com.yowyob.tiibntick.core.marketback.domain.event;

import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import java.time.LocalDateTime;
import java.util.UUID;

/** Domain event — fired when an admin rejects a listing. @author MANFOUO Braun */
public record MarketListingRejectedEvent(
        MarketListingId listingId, UUID adminId, String reason, LocalDateTime occurredAt) implements MarketDomainEvent {
    @Override
    public String aggregateId() { return listingId.toString(); }
}
