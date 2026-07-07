package com.yowyob.tiibntick.core.sales.application.port.in;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Use case — Mark stock as reserved for a confirmed order (CONFIRMED → STOCK_RESERVED).
 *
 * <p>Called after tnt-inventory-core has successfully reserved all line quantities.
 * No Kafka event is published (inventory module already fired its own event).</p>
 *
 * @author MANFOUO Braun
 */
public interface ReserveStockUseCase {
    Mono<TntSalesOrder> reserveStock(UUID tenantId, UUID orderId);
}
