package com.yowyob.tiibntick.core.sales.application.port.in;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Use case — Confirm a TiiBnTick sales order (DRAFT → CONFIRMED).
 *
 * <p>Triggers a {@code SalesOrderConfirmedEvent} published to Kafka
 * so that tnt-inventory-core can proceed with stock reservation.</p>
 *
 * @author MANFOUO Braun
 */
public interface ConfirmSalesOrderUseCase {
    Mono<TntSalesOrder> confirmOrder(UUID tenantId, UUID orderId);
}
