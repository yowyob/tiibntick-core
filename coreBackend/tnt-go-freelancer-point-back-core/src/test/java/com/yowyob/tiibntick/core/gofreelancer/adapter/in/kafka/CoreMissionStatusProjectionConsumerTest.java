package com.yowyob.tiibntick.core.gofreelancer.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.gofreelancer.application.service.CoreDeliveryStatusProjector;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryEventNotifier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Mission status events consumed by gofp")
class CoreMissionStatusProjectionConsumerTest {

    private final CoreDeliveryStatusProjector projector = mock(CoreDeliveryStatusProjector.class);
    private final DeliveryEventNotifier notifier = mock(DeliveryEventNotifier.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final CoreMissionStatusProjectionConsumer consumer =
            new CoreMissionStatusProjectionConsumer(projector, notifier, new ObjectMapper());

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("tnt.delivery.mission.status.changed", 0, 0L, "k", json);
    }

    @Test
    @DisplayName("a well-formed event is projected with its mission id, status and instant, then acknowledged")
    void projectsAndAcks() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        when(projector.project(eq(id), eq("PAUSED_BY_INCIDENT"), any())).thenReturn(Mono.just(true));
        when(notifier.onMissionStatusChanged(any(), any(), any(), any(), any())).thenReturn(Mono.just(2));

        consumer.onMissionStatusChanged(record("{\"missionId\":\"" + id
                + "\",\"eventId\":\"" + eventId + "\",\"tenantId\":\"" + tenant
                + "\",\"trackingCode\":\"TNT-1\",\"newStatus\":\"PAUSED_BY_INCIDENT\",\"occurredAt\":\"2026-11-11T10:00:00Z\"}"), ack);

        verify(projector).project(id, "PAUSED_BY_INCIDENT", Instant.parse("2026-11-11T10:00:00Z"));
        verify(notifier).onMissionStatusChanged(eventId, id, tenant, "PAUSED_BY_INCIDENT", "TNT-1");
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("a projection failure is acknowledged anyway so the partition is not blocked")
    void failureStillAcks() {
        UUID id = UUID.randomUUID();
        when(projector.project(eq(id), any(), any())).thenReturn(Mono.error(new IllegalStateException("db")));
        when(notifier.onMissionStatusChanged(any(), any(), any(), any(), any())).thenReturn(Mono.just(0));

        consumer.onMissionStatusChanged(record("{\"missionId\":\"" + id + "\",\"newStatus\":\"DELIVERED\"}"), ack);

        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("garbage and incomplete events are acknowledged and ignored")
    void garbageIsIgnored() {
        consumer.onMissionStatusChanged(record("not json"), ack);
        consumer.onMissionStatusChanged(record("{\"newStatus\":\"DELIVERED\"}"), ack);

        verify(projector, never()).project(any(), any(), any());
        verify(ack, org.mockito.Mockito.times(2)).acknowledge();
    }
}
