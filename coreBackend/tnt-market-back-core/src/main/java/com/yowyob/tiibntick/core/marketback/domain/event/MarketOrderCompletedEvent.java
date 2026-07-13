package com.yowyob.tiibntick.core.marketback.domain.event;

import com.yowyob.tiibntick.core.marketback.domain.model.MarketOrderId;
import java.time.LocalDateTime;
import java.util.UUID;

/** Domain event — fired when a MarketOrder is completed. @author MANFOUO Braun */
public record MarketOrderCompletedEvent(
        MarketOrderId orderId, UUID providerId, UUID clientId, LocalDateTime occurredAt) implements MarketDomainEvent {
    @Override
    public String aggregateId() { return orderId.toString(); }
}
