package com.yowyob.tiibntick.core.gofreelancer.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryEventNotifier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Lot L3 (QO-18): only {@code DeliveryAlertRaised(DELIVERY_LATE)} notifies; failures reach the container. */
class CoreDeliveryAlertConsumerTest {

    private final DeliveryEventNotifier notifier = mock(DeliveryEventNotifier.class);
    private final CoreDeliveryAlertConsumer consumer = new CoreDeliveryAlertConsumer(notifier, new ObjectMapper(),
            new StaticListableBeanFactory().getBeanProvider(
                    com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport.class));

    private static ConsumerRecord<String, String> record(String eventType, String alertType, UUID eventId,
                                                         UUID deliveryId, UUID tenant) {
        String body = "{\"eventId\":\"" + eventId + "\",\"schemaVersion\":1,\"aggregateType\":\"DELIVERY\","
                + "\"eventType\":\"" + eventType + "\",\"aggregateId\":\"" + deliveryId + "\",\"tenantId\":\""
                + tenant + "\",\"payload\":{},\"details\":{\"alertType\":\"" + alertType + "\"}}";
        return new ConsumerRecord<>(TntTopics.DELIVERY_EVENTS, 0, 0L, deliveryId.toString(), body);
    }

    @Test
    @DisplayName("DELIVERY_LATE notifies client and courier")
    void lateNotifies() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        when(notifier.onDeliveryLate(any(), any(), any(), any())).thenReturn(Mono.just(2));
        consumer.onDeliveryEvent(record("DeliveryAlertRaised", "DELIVERY_LATE", eventId, delivery, tenant));
        verify(notifier).onDeliveryLate(eventId, delivery, tenant, null);
    }

    @Test
    @DisplayName("other alerts and other delivery events are ignored")
    void othersIgnored() throws Exception {
        consumer.onDeliveryEvent(record("DeliveryAlertRaised", "GPS_SIGNAL_LOST", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID()));
        consumer.onDeliveryEvent(record("DeliveryAlertCleared", "DELIVERY_LATE", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID()));
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("a notification failure propagates (retried, then dead-lettered on tnt.dlq)")
    void failurePropagates() {
        when(notifier.onDeliveryLate(any(), any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("down")));
        assertThatThrownBy(() -> consumer.onDeliveryEvent(record("DeliveryAlertRaised", "DELIVERY_LATE",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))).hasMessageContaining("down");
    }
}
