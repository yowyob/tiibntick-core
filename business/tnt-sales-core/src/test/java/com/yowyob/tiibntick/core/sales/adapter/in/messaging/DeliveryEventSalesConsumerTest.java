package com.yowyob.tiibntick.core.sales.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.sales.application.service.SalesApplicationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L8: the sales order follows its delivery from {@code tnt.delivery.events} (pick-up, delivery, failure). */
class DeliveryEventSalesConsumerTest {

    private static final UUID TENANT = UUID.randomUUID();

    private final SalesApplicationService sales = mock(SalesApplicationService.class);
    private final DeliveryEventSalesConsumer consumer = new DeliveryEventSalesConsumer(sales, new ObjectMapper());

    private void send(String type, UUID delivery, String payload) throws Exception {
        consumer.onDeliveryEvent(new ConsumerRecord<>(TntTopics.DELIVERY_EVENTS, 0, 0L, delivery.toString(),
                "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + type + "\",\"schemaVersion\":1,\"aggregateType\":\"DELIVERY\","
                        + "\"aggregateId\":\"" + delivery + "\",\"tenantId\":\"" + TENANT + "\",\"payload\":"
                        + payload + "}"));
    }

    @Test
    @DisplayName("DeliveryFailed: the order is returned with the reason of the failure")
    void failure() throws Exception {
        when(sales.followDelivery(any(), any(), anyString(), any(), anyString())).thenReturn(Mono.empty());
        UUID delivery = UUID.randomUUID();
        send("DeliveryFailed", delivery, "{\"status\":\"FAILED\",\"failureReason\":\"CLIENT_REFUSED_RECEPTION\"}");
        verify(sales).followDelivery(eq(TENANT), eq(delivery), eq("DeliveryFailed"),
                eq(com.yowyob.tiibntick.core.sales.domain.model.ReturnReason.CUSTOMER_REFUSAL), anyString());
    }

    @Test
    @DisplayName("DeliveryPickedUp and DeliveryDelivered are followed; other types and legacy records are ignored")
    void followedTypes() throws Exception {
        when(sales.followDelivery(any(), any(), anyString(), any(), anyString())).thenReturn(Mono.empty());
        UUID delivery = UUID.randomUUID();
        send("DeliveryPickedUp", delivery, "{\"status\":\"PICKED_UP\"}");
        send("DeliveryDelivered", delivery, "{\"status\":\"DELIVERED\"}");
        verify(sales).followDelivery(eq(TENANT), eq(delivery), eq("DeliveryPickedUp"), any(), anyString());
        verify(sales).followDelivery(eq(TENANT), eq(delivery), eq("DeliveryDelivered"), any(), anyString());

        UUID other = UUID.randomUUID();
        send("DeliveryAssigned", other, "{\"status\":\"ASSIGNED\"}");
        consumer.onDeliveryEvent(new ConsumerRecord<>(TntTopics.DELIVERY_EVENTS, 0, 0L, "k",
                "{\"eventType\":\"DeliveryFailed\",\"aggregateId\":\"" + other + "\",\"tenantId\":\"" + TENANT + "\"}"));
        verify(sales, never()).followDelivery(any(), eq(other), anyString(), any(), anyString());
    }

    @Test
    void returnReasons() {
        assertThat(DeliveryEventSalesConsumer.toReturnReason("MAX_ATTEMPTS_REACHED").name()).isEqualTo("RECIPIENT_ABSENT");
        assertThat(DeliveryEventSalesConsumer.toReturnReason("CLIENT_WRONG_ADDRESS").name()).isEqualTo("WRONG_ADDRESS");
        assertThat(DeliveryEventSalesConsumer.toReturnReason("PARCEL_DAMAGED").name()).isEqualTo("DAMAGED_GOODS");
        assertThat(DeliveryEventSalesConsumer.toReturnReason(null).name()).isEqualTo("OTHER");
        assertThat(DeliveryEventSalesConsumer.toReturnReason("SOMETHING").name()).isEqualTo("OTHER");
    }
}
