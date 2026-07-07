package com.yowyob.tiibntick.core.sales.application.port.out;

import com.yowyob.tiibntick.core.sales.domain.model.SalesOrderStatus;
import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for TntSalesOrder persistence.
 * Author: MANFOUO Braun
 */
public interface TntSalesOrderRepository {
    Mono<TntSalesOrder> save(TntSalesOrder order);
    Mono<TntSalesOrder> findById(UUID tenantId, UUID orderId);
    Mono<Boolean> existsByOrderNumber(UUID tenantId, UUID organizationId, String orderNumber);
    Flux<TntSalesOrder> findByClientThirdPartyId(UUID tenantId, UUID clientThirdPartyId);
    Flux<TntSalesOrder> findByAgencyAndPeriod(UUID tenantId, UUID agencyId, int year, int month);
    Flux<TntSalesOrder> findByStatus(UUID tenantId, SalesOrderStatus status);
}
