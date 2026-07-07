package com.yowyob.tiibntick.core.sales.domain.model;

/**
 * Full TiiBnTick order lifecycle — extends the kernel's 3-state model
 * with intermediate logistics states.
 * Author: MANFOUO Braun
 */
public enum SalesOrderStatus {
    DRAFT,
    CONFIRMED,
    STOCK_RESERVED,
    DISPATCHED,
    IN_DELIVERY,
    DELIVERED,
    RETURNED,
    CANCELLED
}
