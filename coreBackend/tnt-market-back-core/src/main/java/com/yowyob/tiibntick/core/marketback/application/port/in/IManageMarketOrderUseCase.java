package com.yowyob.tiibntick.core.marketback.application.port.in;

import com.yowyob.tiibntick.core.marketback.application.port.in.command.*;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.*;
import com.yowyob.tiibntick.core.marketback.domain.model.OrderStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Inbound port — MarketOrder lifecycle use cases.
 * @author MANFOUO Braun
 */
public interface IManageMarketOrderUseCase {

    Mono<MarketOrderResponse> placeOrder(PlaceMarketOrderCommand command);
    Mono<MarketOrderResponse> placeOrderFromQuote(PlaceOrderFromQuoteCommand command);
    Mono<MarketOrderResponse> confirmOrder(UUID orderId, String tenantId);
    Mono<MarketOrderResponse> processPayment(UUID orderId, ProcessPaymentCommand command, String tenantId);
    Mono<MarketOrderResponse> dispatchOrder(UUID orderId, String deliveryMissionId, String tenantId);
    Mono<MarketOrderResponse> markInTransit(UUID orderId, String tenantId);
    Mono<MarketOrderResponse> markDelivered(UUID orderId, String tenantId);
    Mono<MarketOrderResponse> completeOrder(UUID orderId, String tenantId);
    Mono<MarketOrderResponse> cancelOrder(UUID orderId, String reason, String tenantId);
    Mono<MarketOrderResponse> getOrder(UUID orderId, String tenantId);
    Flux<MarketOrderResponse> getOrdersByClient(UUID clientId, String tenantId);
    Flux<MarketOrderResponse> getOrdersByProvider(UUID providerId, String tenantId);
    Flux<MarketOrderResponse> getOrdersByStatus(OrderStatus status, String tenantId);
}
