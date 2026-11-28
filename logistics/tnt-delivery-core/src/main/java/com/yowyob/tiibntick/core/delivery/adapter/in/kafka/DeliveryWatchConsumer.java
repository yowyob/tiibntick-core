package com.yowyob.tiibntick.core.delivery.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.EventPayloads;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.delivery.application.service.watch.DeliveryWatchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * GPS and geofence streams → delivery alerts (lot L3, spec §3.5). Replaces the detection listeners of incident-core,
 * which opened incidents from these topics ([D.1]: only the courier declares an incident). Blocking: a failure is
 * retried, then dead-lettered on {@code tnt.dlq} (lot L7 rule).
 */
@Slf4j
@Component
public class DeliveryWatchConsumer {

    private final DeliveryWatchService watchService;
    private final ObjectMapper objectMapper;
    private final ReactiveListenerSupport listenerSupport;

    public DeliveryWatchConsumer(DeliveryWatchService watchService,
                                 @Qualifier("deliveryObjectMapper") ObjectMapper objectMapper,
                                 ObjectProvider<ReactiveListenerSupport> listenerSupport) {
        this.watchService = watchService;
        this.objectMapper = objectMapper;
        this.listenerSupport = listenerSupport.getIfAvailable(ReactiveListenerSupport::withDefaultTimeout);
    }

    /** A position of a courier on a delivery: records it and raises / clears the GPS alerts. */
    @KafkaListener(
            topics = TntTopics.REALTIME_GPS_POSITION_UPDATED,
            groupId = "${tnt.kafka.consumer.groups.delivery-watch:tnt-delivery-watch}",
            containerFactory = "deliveryKafkaListenerContainerFactory")
    public void onGpsPosition(ConsumerRecord<String, String> record) throws Exception {
        JsonNode payload = EventPayloads.parse(objectMapper, record.value());
        UUID deliveryId = uuid(payload, "missionId");
        if (deliveryId == null) {
            return; // a courier position outside a delivery
        }
        listenerSupport.await(watchService.onPosition(uuid(payload, "tenantId"), deliveryId,
                instant(payload, "occurredAt", "timestamp", "recordedAt"),
                payload.path("trajectoryAnomaly").asBoolean(false),
                payload.path("prolongedStop").asBoolean(false)));
    }

    /** A danger or restricted zone crossed by a courier on a delivery: {@code GEOFENCE_EXIT}. */
    @KafkaListener(
            topics = TntTopics.REALTIME_GEOFENCE_TRIGGERED,
            groupId = "${tnt.kafka.consumer.groups.delivery-watch:tnt-delivery-watch}",
            containerFactory = "deliveryKafkaListenerContainerFactory")
    public void onGeofence(ConsumerRecord<String, String> record) throws Exception {
        JsonNode payload = EventPayloads.parse(objectMapper, record.value());
        // GeofenceTriggerEvent nests the crossing in "trigger" (zoneType, actorId, missionId, coordinates).
        JsonNode trigger = payload.has("trigger") ? payload.path("trigger") : payload;
        String zoneType = trigger.path("zoneType").asText("");
        UUID deliveryId = uuid(trigger, "missionId");
        if (deliveryId == null || !("DANGER_ZONE".equals(zoneType) || "RESTRICTED_ZONE".equals(zoneType))) {
            return;
        }
        UUID tenantId = uuid(payload, "tenantId");
        listenerSupport.await(watchService.onGeofence(tenantId != null ? tenantId : uuid(trigger, "tenantId"),
                deliveryId, zoneType));
    }

    /** A present but malformed id fails the record (dead-lettered); an absent one is {@code null}. */
    private static UUID uuid(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) {
            return null;
        }
        return UUID.fromString(v.asText());
    }

    private static Instant instant(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && v.isTextual()) {
                try {
                    return Instant.parse(v.asText());
                } catch (DateTimeParseException e) {
                    log.debug("Unparsable {} '{}' on GPS event", f, v.asText());
                }
            }
        }
        return null;
    }
}
