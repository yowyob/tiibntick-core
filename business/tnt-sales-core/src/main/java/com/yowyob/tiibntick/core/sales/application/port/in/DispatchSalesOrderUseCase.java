package com.yowyob.tiibntick.core.sales.application.port.in;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Use case — Dispatch a sales order to a delivery mission (STOCK_RESERVED → DISPATCHED).
 *
 * <p>Links the order to a delivery mission ID and publishes {@code SalesOrderDispatchedEvent}.</p>
 *
 * @author MANFOUO Braun
 */
public interface DispatchSalesOrderUseCase {
    Mono<TntSalesOrder> dispatch(UUID tenantId, UUID orderId, UUID missionId);
}
