package com.yowyob.tiibntick.core.sales.domain.event;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: Sales order was confirmed and ready for stock reservation.
 * Author: MANFOUO Braun
 */
public record SalesOrderConfirmedEvent(
        UUID orderId, UUID tenantId, UUID organizationId, UUID agencyId,
        UUID clientThirdPartyId, String orderNumber, BigDecimal totalAmount,
        String currency, String priority, Instant occurredAt) {

    public static SalesOrderConfirmedEvent of(TntSalesOrder o) {
        return new SalesOrderConfirmedEvent(o.getId(), o.getTenantId(), o.getOrganizationId(),
                o.getAgencyId(), o.getClientThirdPartyId(), o.getOrderNumber(),
                o.getTotalAmount(), o.getCurrency(), o.getPriority().name(), Instant.now());
    }
}
