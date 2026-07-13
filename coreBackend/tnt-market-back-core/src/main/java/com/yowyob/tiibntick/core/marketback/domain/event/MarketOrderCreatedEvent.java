package com.yowyob.tiibntick.core.marketback.domain.event;

import com.yowyob.tiibntick.core.marketback.domain.model.MarketOrderId;
import com.yowyob.tiibntick.core.marketback.domain.model.Money;
import java.time.LocalDateTime;
import java.util.UUID;

/** Domain event — fired when a MarketOrder is created. @author MANFOUO Braun */
public record MarketOrderCreatedEvent(
        MarketOrderId orderId, UUID clientId, UUID providerId,
        String tenantId, Money totalAmount, LocalDateTime occurredAt) implements MarketDomainEvent {
    @Override
    public String aggregateId() { return orderId.toString(); }
}
