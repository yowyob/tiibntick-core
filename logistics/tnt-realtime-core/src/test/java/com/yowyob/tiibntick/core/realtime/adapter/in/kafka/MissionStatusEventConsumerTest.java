package com.yowyob.tiibntick.core.realtime.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.realtime.application.port.in.IBroadcastNotificationUseCase;
import com.yowyob.tiibntick.core.realtime.domain.service.MissionTrackingCodeCache;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;
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

/** Lot L8: WebSocket pushes of the delivery and parcel transitions. */
class MissionStatusEventConsumerTest {

    private static final String TENANT = UUID.randomUUID().toString();

    private final IBroadcastNotificationUseCase broadcast = mock(IBroadcastNotificationUseCase.class);
    private final MissionTrackingCodeCache cache = new MissionTrackingCodeCache();
    private final MissionStatusEventConsumer consumer =
            new MissionStatusEventConsumer(broadcast, new ObjectMapper(), cache);
    private final Acknowledgment ack = mock(Acknowledgment.class);

    private static ConsumerRecord<String, String> record(String topic, String id, String previous, String payload) {
        String prev = previous == null ? "null" : "{\"status\":\"" + previous + "\"}";
        return new ConsumerRecord<>(topic, 0, 0L, id, "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"X\","
                + "\"schemaVersion\":1,\"aggregateType\":\"" + (topic.equals(TntTopics.PARCEL_EVENTS) ? "PARCEL" : "DELIVERY")
                + "\",\"aggregateId\":\"" + id + "\",\"tenantId\":\"" + TENANT + "\","
                + "\"occurredAt\":\"2026-11-27T10:00:00Z\",\"previousState\":" + prev + ",\"payload\":" + payload + "}");
    }

    @Test
    @DisplayName("the parcel event remembers the code of its delivery and pushes the parcel states on the code topic; "
            + "the delivery transition then goes to the delivery topic with that code")
    void parcelThenDelivery() throws Exception {
        when(broadcast.broadcastToTopic(anyString(), any())).thenReturn(Mono.empty());
        consumer.onParcelEvent(record(TntTopics.PARCEL_EVENTS, "p-1", "IN_TRANSIT",
                "{\"status\":\"AT_RELAY_HUB\",\"trackingCode\":\"TNT-9\",\"currentDeliveryId\":\"d-1\"}"), ack);
        consumer.onDeliveryEvent(record(TntTopics.DELIVERY_EVENTS, "d-1", "IN_TRANSIT",
                "{\"status\":\"AT_RELAY_HUB\",\"agencyId\":\"a-1\",\"courierId\":\"c-1\"}"), ack);

        assertThat(cache.get("d-1")).isEqualTo("TNT-9");
        ArgumentCaptor<Object> tracking = ArgumentCaptor.forClass(Object.class);
        verify(broadcast).broadcastToTopic(eq("/topic/tracking/TNT-9"), tracking.capture());
        assertThat(tracking.getValue()).isEqualTo(new MissionStatusEventConsumer.TrackingStatusPayload(
                "TNT-9", "IN_TRANSIT", "AT_RELAY_HUB", "2026-11-27T10:00:00Z"));
        ArgumentCaptor<Object> mission = ArgumentCaptor.forClass(Object.class);
        verify(broadcast).broadcastToTopic(eq("/topic/delivery/d-1"), mission.capture());
        assertThat(mission.getValue()).isEqualTo(new MissionStatusEventConsumer.MissionStatusPayload("d-1", "TNT-9",
                TENANT, "IN_TRANSIT", "AT_RELAY_HUB", "a-1", "c-1", "2026-11-27T10:00:00Z"));
    }

    @Test
    @DisplayName("a change without transition and a legacy record are not pushed")
    void noPush() throws Exception {
        consumer.onDeliveryEvent(record(TntTopics.DELIVERY_EVENTS, "d-2", "IN_TRANSIT", "{\"status\":\"IN_TRANSIT\"}"),
                ack);
        consumer.onDeliveryEvent(new ConsumerRecord<>(TntTopics.DELIVERY_EVENTS, 0, 0L, "d-2",
                "{\"missionId\":\"d-2\",\"newStatus\":\"DELIVERED\"}"), ack);
        verify(broadcast, never()).broadcastToTopic(anyString(), any());
    }
}
