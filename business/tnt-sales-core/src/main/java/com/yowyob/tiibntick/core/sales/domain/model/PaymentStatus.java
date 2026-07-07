package com.yowyob.tiibntick.core.sales.domain.model;

/** Payment lifecycle for a SalesOrder. Author: MANFOUO Braun */
public enum PaymentStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
    PENDING,
    REFUNDED,
    CANCELLED
}
