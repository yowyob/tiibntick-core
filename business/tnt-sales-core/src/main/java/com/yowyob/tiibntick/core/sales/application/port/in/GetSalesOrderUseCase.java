package com.yowyob.tiibntick.core.sales.application.port.in;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Use case — Retrieve a single TiiBnTick sales order by its ID.
 *
 * @author MANFOUO Braun
 */
public interface GetSalesOrderUseCase {
    Mono<TntSalesOrder> getOrder(UUID tenantId, UUID orderId);
}
