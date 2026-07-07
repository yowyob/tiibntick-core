package com.yowyob.tiibntick.core.sales.domain.event;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event: Order was successfully delivered to the recipient.
 * Author: MANFOUO Braun
 */
public record SalesOrderDeliveredEvent(
        UUID orderId, UUID tenantId, UUID organizationId,
        UUID clientThirdPartyId, String orderNumber,
        BigDecimal totalAmount, String currency, Instant occurredAt) {

    public static SalesOrderDeliveredEvent of(TntSalesOrder o) {
        return new SalesOrderDeliveredEvent(o.getId(), o.getTenantId(), o.getOrganizationId(),
                o.getClientThirdPartyId(), o.getOrderNumber(), o.getTotalAmount(),
                o.getCurrency(), Instant.now());
    }
}
