package com.yowyob.tiibntick.core.sales.application.port.in;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;
import reactor.core.publisher.Mono;

/**
 * Use case — Create a new TiiBnTick sales order (inbound port).
 *
 * <p>Generates an order number, builds the aggregate, and persists it in DRAFT status.
 * Optionally resolves a Kernel sales order reference (kernelSalesOrderId) before saving.</p>
 *
 * @author MANFOUO Braun
 */
public interface CreateTntSalesOrderUseCase {
    Mono<TntSalesOrder> createOrder(CreateTntSalesOrderCommand cmd);
}
