package com.yowyob.tiibntick.core.marketback.domain.model;

/** Lifecycle status of a MarketOrder. @author MANFOUO Braun */
public enum OrderStatus {
    DRAFT,
    CONFIRMED,
    PAID,
    DISPATCHED,
    IN_TRANSIT,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    REFUND_REQUESTED,
    REFUNDED
}
