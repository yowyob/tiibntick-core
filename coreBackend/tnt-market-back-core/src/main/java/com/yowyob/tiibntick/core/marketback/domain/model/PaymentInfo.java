package com.yowyob.tiibntick.core.marketback.domain.model;

import java.time.LocalDateTime;

/**
 * Value Object — payment confirmation data for a MarketOrder.
 * @author MANFOUO Braun
 */
public record PaymentInfo(
        PaymentMethod paymentMethod,
        String transactionRef,
        LocalDateTime paidAt,
        Money paidAmount,
        String mobileMoneyPhone
) {
    public boolean isPaid() {
        return transactionRef != null && paidAt != null;
    }
}
