package com.yowyob.tiibntick.core.sales.domain.event;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;

import java.time.Instant;
import java.util.UUID;

/**
 * Event: Order was cancelled.
 * Author: MANFOUO Braun
 */
public record SalesOrderCancelledEvent(
        UUID orderId, UUID tenantId, UUID organizationId,
        String orderNumber, String cancelReason, Instant occurredAt) {

    public static SalesOrderCancelledEvent of(TntSalesOrder o) {
        return new SalesOrderCancelledEvent(o.getId(), o.getTenantId(), o.getOrganizationId(),
                o.getOrderNumber(), o.getCancelReason(), Instant.now());
    }
}
