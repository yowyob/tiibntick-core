package com.yowyob.tiibntick.core.agency.assignment.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.agency.assignment.application.service.MissionService;
import com.yowyob.tiibntick.core.agency.assignment.application.service.MissionService.CoreMissionSyncHint;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L8: the agency projection of a delivery follows the transitions of {@code tnt.delivery.events}. */
class DeliveryStatusConsumerTest {

    private static final UUID TENANT = UUID.randomUUID();

    private final MissionService missions = mock(MissionService.class);
    private final DeliveryStatusConsumer consumer = new DeliveryStatusConsumer(missions, new ObjectMapper());
    private final Acknowledgment ack = mock(Acknowledgment.class);

    private void send(UUID delivery, String previous, String payload) throws Exception {
        String prev = previous == null ? "null" : "{\"status\":\"" + previous + "\"}";
        consumer.onDeliveryEvent(new ConsumerRecord<>(TntTopics.DELIVERY_EVENTS, 0, 0L, delivery.toString(),
                "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"X\",\"schemaVersion\":1,\"aggregateType\":\"DELIVERY\","
                        + "\"aggregateId\":\"" + delivery + "\",\"tenantId\":\"" + TENANT + "\",\"previousState\":"
                        + prev + ",\"payload\":" + payload + "}"), ack);
    }

    @Test
    @DisplayName("a transition syncs the projection with the masked hints (district, city, display name, hub)")
    void transitionSynced() throws Exception {
        when(missions.syncFromCoreStatus(any(), any(), anyString(), any())).thenReturn(Mono.empty());
        UUID delivery = UUID.randomUUID();
        UUID agency = UUID.randomUUID();
        UUID hub = UUID.randomUUID();
        send(delivery, "IN_TRANSIT", "{\"status\":\"AT_RELAY_HUB\",\"agencyId\":\"" + agency + "\","
                + "\"pickupAddress\":{\"district\":\"Mokolo\",\"city\":\"Yaoundé\"},"
                + "\"deliveryAddress\":{\"city\":\"Douala\"},\"recipientDisplayName\":\"Jean D.\","
                + "\"relayHubId\":\"" + hub + "\"}");

        ArgumentCaptor<CoreMissionSyncHint> hint = ArgumentCaptor.forClass(CoreMissionSyncHint.class);
        verify(missions).syncFromCoreStatus(eq(TENANT), eq(delivery), eq("AT_RELAY_HUB"), hint.capture());
        assertThat(hint.getValue().agencyId()).isEqualTo(agency);
        assertThat(hint.getValue().pickupAddress()).isEqualTo("Mokolo, Yaoundé");
        assertThat(hint.getValue().deliveryAddress()).isEqualTo("Douala");
        assertThat(hint.getValue().recipientName()).isEqualTo("Jean D.");
        assertThat(hint.getValue().recipientPhone()).isNull();
        assertThat(hint.getValue().targetHubId()).isEqualTo(hub);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("a change without transition and a legacy record are acked without sync")
    void noTransition() throws Exception {
        UUID delivery = UUID.randomUUID();
        send(delivery, "IN_TRANSIT", "{\"status\":\"IN_TRANSIT\"}");
        consumer.onDeliveryEvent(new ConsumerRecord<>(TntTopics.DELIVERY_EVENTS, 0, 0L, "k",
                "{\"eventType\":\"MissionStatusChangedEvent\",\"newStatus\":\"DELIVERED\"}"), ack);
        verify(missions, never()).syncFromCoreStatus(any(), any(), anyString(), any());
        verify(ack, times(2)).acknowledge();
    }
}
