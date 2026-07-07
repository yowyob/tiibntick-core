package com.yowyob.tiibntick.core.sales.application.port.in;

import com.yowyob.tiibntick.core.sales.domain.model.SalesOrderStatus;
import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;
import reactor.core.publisher.Flux;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Use case — Query/list TiiBnTick sales orders with various filters.
 *
 * @author MANFOUO Braun
 */
public interface ListSalesOrdersUseCase {
    Flux<TntSalesOrder> listByClient(UUID tenantId, UUID clientThirdPartyId);
    Flux<TntSalesOrder> listByAgency(UUID tenantId, UUID agencyId, YearMonth period);
    Flux<TntSalesOrder> listByStatus(UUID tenantId, SalesOrderStatus status);
    /** Shorthand: list all orders in STOCK_RESERVED status (ready for dispatch). */
    Flux<TntSalesOrder> listPendingDispatch(UUID tenantId);
}
