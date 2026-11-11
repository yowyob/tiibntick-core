package com.yowyob.tiibntick.core.gofreelancer.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.gofreelancer.application.service.CoreDeliveryStatusProjector;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryEventNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound Kafka adapter: projects every mission status change published by tnt-delivery-core onto
 * the gofp delivery row (see {@link CoreDeliveryStatusProjector}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoreMissionStatusProjectionConsumer {

    private final CoreDeliveryStatusProjector projector;
    private final DeliveryEventNotifier notifier;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TntTopics.DELIVERY_MISSION_STATUS_CHANGED,
            groupId = "tnt-gofp-delivery-projection",
            containerFactory = "gofpKafkaListenerContainerFactory")
    public void onMissionStatusChanged(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String missionId = payload.path("missionId").asText(null);
            String newStatus = payload.path("newStatus").asText(null);
            if (missionId == null || newStatus == null) {
                log.warn("Mission status event without missionId/newStatus ignored (key={})", record.key());
                ack.acknowledge();
                return;
            }
            String at = payload.path("occurredAt").asText(null);
            Instant occurredAt = at != null ? parse(at) : Instant.now();
            UUID deliveryId = UUID.fromString(missionId);
            UUID eventId = uuidOrNull(payload.path("eventId").asText(null));
            UUID tenantId = uuidOrNull(payload.path("tenantId").asText(null));
            String trackingCode = payload.path("trackingCode").asText(null);
            projector.project(deliveryId, newStatus, occurredAt)
                    // Whoever caused the change, the people involved hear about it (failure-tolerant).
                    .then(Mono.defer(() -> notifier.onMissionStatusChanged(eventId, deliveryId, tenantId, newStatus, trackingCode)
                            .onErrorResume(e -> {
                                log.warn("Notification for mission {} {} failed: {}", missionId, newStatus, e.getMessage());
                                return Mono.just(0);
                            })))
                    .doOnError(e -> log.error("Failed to project mission {} status {}: {}", missionId, newStatus, e.getMessage()))
                    .onErrorReturn(0)
                    .doFinally(s -> ack.acknowledge())
                    .subscribe();
        } catch (Exception e) {
            log.error("Unreadable mission status event (key={}): {}", record.key(), e.getMessage());
            ack.acknowledge();
        }
    }

    private static UUID uuidOrNull(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }

    private static Instant parse(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception notIso) {
            return Instant.now();
        }
    }
}
