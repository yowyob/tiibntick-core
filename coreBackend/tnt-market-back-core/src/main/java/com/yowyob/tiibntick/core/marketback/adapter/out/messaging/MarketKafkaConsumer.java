package com.yowyob.tiibntick.core.marketback.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketOrderUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MarketOrderResponse;
import com.yowyob.tiibntick.core.marketback.domain.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Kafka consumer — reacts to delivery and payment events from
 * tnt-delivery-core / tnt-billing-wallet (published on shared topics declared
 * in tnt-bootstrap's {@code TntKafkaTopicsConfig}) to update MarketOrder state.
 *
 * <p>Topic names are the literal strings from {@code TntKafkaTopicsConfig}
 * ({@code tntMissionStatusChanged} / {@code tntPaymentConfirmed}) — this
 * module has no local {@code kafkaTopics} bean, unlike the original
 * standalone tiibntick-market-backend app.
 *
 * @author MANFOUO Braun
 */
/**
 * Market order reactions to delivery and payment events.
 *
 * <p>Lot L7 (spec §3.10): both listeners await their work on the container thread
 * ({@link ReactiveListenerSupport}); any failure (unreadable record, unknown order, illegal transition, database
 * error) reaches the container: retries, then {@code tnt.dlq}. A transition the order has already reached or passed
 * is a no-op, so a replayed or duplicated event never dead-letters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketKafkaConsumer {

    private final IManageMarketOrderUseCase orderUseCase;
    private final ObjectMapper objectMapper;
    private final ReactiveListenerSupport listenerSupport;

    /**
     * Lot L8: transitions of the core delivery an order was dispatched with ({@code tnt.delivery.events}; the order
     * is found by its {@code missionId}, set by {@code dispatchOrder}). Picked up / in transit → IN_TRANSIT, delivered
     * → DELIVERED. Other types and legacy records are ignored.
     */
    @KafkaListener(topics = TntTopics.DELIVERY_EVENTS, groupId = "tnt-market-group",
            containerFactory = "marketKafkaListenerContainerFactory")
    public void onMissionStatusChanged(String payload) throws IOException {
        JsonNode event = objectMapper.readTree(payload);
        String status = event.path("payload").path("status").asText(null);
        if (!com.yowyob.tiibntick.common.kafka.TntAggregateEvent.isAggregateEvent(event) || status == null
                || status.equals(event.path("previousState").path("status").asText(null))
                || !event.hasNonNull("tenantId")) {
            return;
        }
        UUID deliveryId = UUID.fromString(event.path("aggregateId").asText());
        String tenantId = event.path("tenantId").asText();
        listenerSupport.await(orderUseCase.getOrdersByDelivery(deliveryId, tenantId)
                .concatMap(order -> {
                    UUID orderId = order.id();
                    log.debug("Delivery {} {}: market order {}", deliveryId, status, orderId);
                    Mono<?> work = switch (status) {
                        case "PICKED_UP", "IN_TRANSIT" -> advance(orderId, tenantId, OrderStatus.DISPATCHED,
                                OrderStatus.IN_TRANSIT, () -> orderUseCase.markInTransit(orderId, tenantId));
                        case "DELIVERED" -> advance(orderId, tenantId, OrderStatus.DISPATCHED, OrderStatus.IN_TRANSIT,
                                () -> orderUseCase.markInTransit(orderId, tenantId))
                                .then(advance(orderId, tenantId, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED,
                                        () -> orderUseCase.markDelivered(orderId, tenantId)));
                        default -> Mono.empty();
                    };
                    return work;
                })
                .then());
    }

    @KafkaListener(topics = TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED, groupId = "tnt-market-group",
            containerFactory = "marketKafkaListenerContainerFactory")
    public void onPaymentConfirmed(String payload) throws IOException {
        JsonNode node = objectMapper.readTree(payload);
        if (!node.hasNonNull("orderId")) {
            // Settlement-level confirmations (Core SettlementService, R1 2026-11-26) carry no orderId:
            // a market order is paid through processPayment, which records its own settlement.
            log.debug("paymentConfirmed without orderId (settlement={}), ignored",
                    node.path("settlementId").asText("?"));
            return;
        }
        UUID orderId = UUID.fromString(node.get("orderId").asText());
        String tenantId = node.get("tenantId").asText();
        log.debug("Received payment confirmation for orderId={}", orderId);
        listenerSupport.await(advance(orderId, tenantId, OrderStatus.DRAFT, OrderStatus.CONFIRMED,
                () -> orderUseCase.confirmOrder(orderId, tenantId)));
    }

    /**
     * Applies {@code transition} when the order is in {@code from}; an order already in {@code to} or further along
     * the main flow (DRAFT → … → COMPLETED) is left as is. Any other state is handed to the transition, whose
     * domain guard raises the error (then retries, then {@code tnt.dlq}).
     */
    private Mono<?> advance(UUID orderId, String tenantId, OrderStatus from, OrderStatus to,
                            Supplier<Mono<MarketOrderResponse>> transition) {
        return orderUseCase.getOrder(orderId, tenantId).flatMap(order -> {
            OrderStatus current = order.status();
            if (current != from && reached(current, to)) {
                log.debug("Market order {} already {} (target {}): event ignored", orderId, current, to);
                return Mono.just(order);
            }
            return transition.get();
        });
    }

    /** @return whether {@code current} is {@code target} or after it on the main order flow */
    static boolean reached(OrderStatus current, OrderStatus target) {
        return current.ordinal() >= target.ordinal() && current.ordinal() <= OrderStatus.COMPLETED.ordinal();
    }
}
