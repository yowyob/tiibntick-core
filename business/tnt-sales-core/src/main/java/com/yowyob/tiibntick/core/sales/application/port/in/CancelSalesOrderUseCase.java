package com.yowyob.tiibntick.core.sales.application.port.in;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Use case — Cancel a sales order (any non-DELIVERED status → CANCELLED).
 *
 * <p>Publishes {@code SalesOrderCancelledEvent} so that tnt-inventory-core
 * can release any reserved stock.</p>
 *
 * @author MANFOUO Braun
 */
public interface CancelSalesOrderUseCase {
    Mono<TntSalesOrder> cancelOrder(UUID tenantId, UUID orderId, String reason);
}
