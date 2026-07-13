package com.yowyob.tiibntick.core.marketback.domain.event;

import com.yowyob.tiibntick.core.marketback.domain.model.MarketOrderId;
import com.yowyob.tiibntick.core.marketback.domain.model.Money;
import com.yowyob.tiibntick.core.marketback.domain.model.PaymentMethod;
import java.time.LocalDateTime;

/** Domain event — fired when a MarketOrder payment is confirmed. @author MANFOUO Braun */
public record MarketOrderPaidEvent(
        MarketOrderId orderId, String transactionRef,
        PaymentMethod paymentMethod, Money amount, LocalDateTime occurredAt) implements MarketDomainEvent {
    @Override
    public String aggregateId() { return orderId.toString(); }
}
