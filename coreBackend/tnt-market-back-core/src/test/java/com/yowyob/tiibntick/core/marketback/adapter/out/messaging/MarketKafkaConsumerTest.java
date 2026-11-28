package com.yowyob.tiibntick.core.marketback.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketOrderUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MarketOrderResponse;
import com.yowyob.tiibntick.core.marketback.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L7: blocking listeners; a replayed transition is a no-op, any other failure reaches the container. */
class MarketKafkaConsumerTest {

    private static final String TENANT = UUID.randomUUID().toString();

    private final IManageMarketOrderUseCase orders = mock(IManageMarketOrderUseCase.class);
    private final MarketKafkaConsumer consumer = new MarketKafkaConsumer(orders, new ObjectMapper(),
            ReactiveListenerSupport.withDefaultTimeout());

    private MarketOrderResponse order(UUID id, OrderStatus status) {
        MarketOrderResponse response = mock(MarketOrderResponse.class);
        when(response.id()).thenReturn(id);
        when(response.status()).thenReturn(status);
        return response;
    }

    /** Lot L8: a delivery transition on tnt.delivery.events. */
    private static String delivery(UUID deliveryId, String previous, String status) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"DeliveryX\",\"schemaVersion\":1,"
                + "\"aggregateType\":\"DELIVERY\",\"aggregateId\":\"" + deliveryId + "\",\"tenantId\":\"" + TENANT
                + "\",\"previousState\":{\"status\":\"" + previous + "\"},\"payload\":{\"status\":\"" + status
                + "\"},\"details\":{}}";
    }

    private UUID dispatchedWith(UUID deliveryId, MarketOrderResponse order) {
        when(orders.getOrdersByDelivery(deliveryId, TENANT)).thenReturn(reactor.core.publisher.Flux.just(order));
        when(orders.getOrder(order.id(), TENANT)).thenReturn(Mono.just(order));
        return order.id();
    }

    @Test
    void aPickedUpDeliveryPutsItsDispatchedOrderInTransit() throws Exception {
        UUID delivery = UUID.randomUUID();
        UUID id = dispatchedWith(delivery, order(UUID.randomUUID(), OrderStatus.DISPATCHED));
        MarketOrderResponse next = order(id, OrderStatus.IN_TRANSIT);
        when(orders.markInTransit(id, TENANT)).thenReturn(Mono.just(next));

        consumer.onMissionStatusChanged(delivery(delivery, "ASSIGNED", "PICKED_UP"));

        verify(orders).markInTransit(id, TENANT);
    }

    @Test
    void aReplayedTransitionIsANoOp() throws Exception {
        UUID delivery = UUID.randomUUID();
        dispatchedWith(delivery, order(UUID.randomUUID(), OrderStatus.DELIVERED));

        consumer.onMissionStatusChanged(delivery(delivery, "PICKED_UP", "IN_TRANSIT"));
        consumer.onMissionStatusChanged(delivery(delivery, "IN_TRANSIT", "DELIVERED"));

        verify(orders, never()).markInTransit(any(), anyString());
        verify(orders, never()).markDelivered(any(), anyString());
    }

    @Test
    void anIllegalTransitionReachesTheContainer() {
        UUID delivery = UUID.randomUUID();
        UUID id = dispatchedWith(delivery, order(UUID.randomUUID(), OrderStatus.CONFIRMED));
        when(orders.markInTransit(id, TENANT)).thenReturn(Mono.error(new IllegalStateException("not DISPATCHED")));
        assertThatThrownBy(() -> consumer.onMissionStatusChanged(delivery(delivery, "PICKED_UP", "IN_TRANSIT")))
                .hasMessageContaining("not DISPATCHED");
    }

    @Test
    void deliveriesWithoutAMarketOrderAndLegacyRecordsAreIgnored() throws Exception {
        UUID delivery = UUID.randomUUID();
        when(orders.getOrdersByDelivery(delivery, TENANT)).thenReturn(reactor.core.publisher.Flux.empty());
        consumer.onMissionStatusChanged(delivery(delivery, "IN_TRANSIT", "DELIVERED"));
        consumer.onMissionStatusChanged("{\"marketOrderId\":\"" + UUID.randomUUID() + "\",\"newStatus\":\"DELIVERED\"}");
        consumer.onPaymentConfirmed("{\"settlementId\":\"s\"}");
        verify(orders, never()).getOrder(any(), anyString());
    }

    @Test
    void anUnreadableRecordReachesTheContainer() {
        assertThatThrownBy(() -> consumer.onPaymentConfirmed("not json")).isNotNull();
    }

    @Test
    void aPaymentConfirmsADraftOrderOnlyOnce() throws Exception {
        UUID id = UUID.randomUUID();
        MarketOrderResponse draft = order(id, OrderStatus.DRAFT);
        when(orders.getOrder(id, TENANT)).thenReturn(Mono.just(draft));
        when(orders.confirmOrder(id, TENANT)).thenReturn(Mono.just(draft));
        String payment = "{\"orderId\":\"" + id + "\",\"tenantId\":\"" + TENANT + "\"}";

        consumer.onPaymentConfirmed(payment);
        verify(orders).confirmOrder(id, TENANT);

        MarketOrderResponse paid = order(id, OrderStatus.PAID);
        when(orders.getOrder(id, TENANT)).thenReturn(Mono.just(paid));
        consumer.onPaymentConfirmed(payment);
        verify(orders).confirmOrder(id, TENANT);
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void reachedFollowsTheMainFlowOnly(OrderStatus status) {
        boolean expected = status.ordinal() >= OrderStatus.DISPATCHED.ordinal()
                && status.ordinal() <= OrderStatus.COMPLETED.ordinal();
        assertThat(MarketKafkaConsumer.reached(status, OrderStatus.DISPATCHED)).isEqualTo(expected);
    }
}
