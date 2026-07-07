package com.yowyob.tiibntick.core.sales.application.port.in;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Use case — Mark order as delivered (IN_DELIVERY → DELIVERED).
 *
 * <p>Publishes {@code SalesOrderDeliveredEvent} consumed by tnt-accounting-core
 * to generate the invoice and by tnt-inventory-core to finalise stock consumption.</p>
 *
 * @author MANFOUO Braun
 */
public interface MarkDeliveredUseCase {
    Mono<TntSalesOrder> markDelivered(UUID tenantId, UUID orderId);
}
