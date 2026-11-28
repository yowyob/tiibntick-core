package com.yowyob.tiibntick.core.gofreelancer.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryEventNotifier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Lot L8: delivery transitions of tnt.delivery.events consumed by gofp")
class CoreMissionStatusConsumerTest {

    private final DeliveryEventNotifier notifier = mock(DeliveryEventNotifier.class);
    private final IDeliveryLifecyclePort lifecycle = mock(IDeliveryLifecyclePort.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ReactiveListenerSupport> support = mock(ObjectProvider.class);

    private CoreMissionStatusConsumer consumer() {
        when(support.getIfAvailable(any())).thenReturn(ReactiveListenerSupport.withDefaultTimeout());
        return new CoreMissionStatusConsumer(notifier, lifecycle, new ObjectMapper(), support);
    }

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("tnt.delivery.events", 0, 0L, "k", json);
    }

    private static String event(UUID eventId, UUID delivery, UUID tenant, String previous, String status) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"DeliveryPausedByIncident\",\"schemaVersion\":1,"
                + "\"aggregateType\":\"DELIVERY\",\"aggregateId\":\"" + delivery + "\",\"tenantId\":\"" + tenant
                + "\",\"previousState\":" + (previous == null ? "null" : "{\"status\":\"" + previous + "\"}")
                + ",\"payload\":{\"status\":\"" + status + "\"},\"details\":{}}";
    }

    @Test
    @DisplayName("a notified transition reads the tracking code from the core, notifies, then acknowledges")
    void notifiesAndAcks() throws Exception {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        when(lifecycle.findStateAnyTenant(id)).thenReturn(Mono.just(new IDeliveryLifecyclePort.CoreDeliveryState(
                id, "PAUSED_BY_INCIDENT", "TNT-1", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null)));
        when(notifier.onDeliveryStatusChanged(any(), any(), any(), any(), any())).thenReturn(Mono.just(2));

        consumer().onDeliveryStatusChanged(record(event(eventId, id, tenant, "IN_TRANSIT", "PAUSED_BY_INCIDENT")), ack);

        verify(notifier).onDeliveryStatusChanged(eventId, id, tenant, "PAUSED_BY_INCIDENT", "TNT-1");
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("a notification failure reaches the container (retries, then tnt.dlq) and is not acknowledged")
    void failureIsRethrown() {
        UUID id = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        when(lifecycle.findStateAnyTenant(any())).thenReturn(Mono.empty());
        when(notifier.onDeliveryStatusChanged(any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("db")));

        assertThatThrownBy(() -> consumer().onDeliveryStatusChanged(
                record(event(UUID.randomUUID(), id, tenant, "IN_TRANSIT", "DELIVERED")), ack)).hasMessageContaining("db");
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("legacy records, changes without transition and statuses not notified are acknowledged and ignored")
    void ignored() throws Exception {
        UUID id = UUID.randomUUID();
        CoreMissionStatusConsumer consumer = consumer();
        consumer.onDeliveryStatusChanged(record("{\"missionId\":\"" + id + "\",\"newStatus\":\"DELIVERED\"}"), ack);
        consumer.onDeliveryStatusChanged(record(event(UUID.randomUUID(), id, UUID.randomUUID(), "IN_TRANSIT",
                "IN_TRANSIT")), ack);
        consumer.onDeliveryStatusChanged(record(event(UUID.randomUUID(), id, UUID.randomUUID(), "CREATED",
                "ASSIGNED")), ack);

        verify(notifier, never()).onDeliveryStatusChanged(any(), any(), any(), any(), any());
        verify(ack, times(3)).acknowledge();
    }
}
