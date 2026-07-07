package com.yowyob.tiibntick.core.sales.domain.event;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;

import java.time.Instant;
import java.util.UUID;

/**
 * Event: Order was dispatched with a delivery mission.
 * Author: MANFOUO Braun
 */
public record SalesOrderDispatchedEvent(
        UUID orderId, UUID tenantId, UUID organizationId, UUID missionId,
        String orderNumber, Instant occurredAt) {

    public static SalesOrderDispatchedEvent of(TntSalesOrder o) {
        return new SalesOrderDispatchedEvent(o.getId(), o.getTenantId(), o.getOrganizationId(),
                o.getMissionId(), o.getOrderNumber(), Instant.now());
    }
}
