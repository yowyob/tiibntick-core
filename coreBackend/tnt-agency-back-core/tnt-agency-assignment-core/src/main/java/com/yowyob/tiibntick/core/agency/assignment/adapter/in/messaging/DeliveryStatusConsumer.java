package com.yowyob.tiibntick.core.agency.assignment.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.agency.assignment.application.service.MissionService;
import com.yowyob.tiibntick.core.agency.assignment.application.service.MissionService.CoreMissionSyncHint;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Keeps the agency mission projection in step with the core delivery (lot L8, spec §3.7): every status transition of
 * {@code tnt.delivery.events} (creation included, status {@code CREATED}) is applied by
 * {@link MissionService#syncFromCoreStatus}; the projection is created from the masked snapshot when missing
 * (agency, district/city of the addresses, recipient display name, relay hub). Replaces the listeners of
 * {@code tnt.delivery.mission.created / mission.status.changed / package.delivered}. Changes without a transition and
 * legacy records are ignored. The work is awaited (lot L7 rule): a failure is retried, then dead-lettered.
 */
@Component
@ConditionalOnProperty(name = "tnt.agency.kafka.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryStatusConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeliveryStatusConsumer.class);

    private final MissionService missionService;
    private final ObjectMapper objectMapper;

    public DeliveryStatusConsumer(MissionService missionService, ObjectMapper objectMapper) {
        this.missionService = missionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = com.yowyob.tiibntick.common.kafka.TntTopics.DELIVERY_EVENTS,
            groupId = "${tnt.kafka.topics.groups.agency-erp-delivery:tnt-agency-erp-delivery-consumer}",
            containerFactory = "agencyAssignmentKafkaListenerFactory"
    )
    public void onDeliveryEvent(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        com.fasterxml.jackson.databind.JsonNode event = objectMapper.readTree(record.value());
        com.fasterxml.jackson.databind.JsonNode p = event.path("payload");
        String status = p.path("status").asText(null);
        String previous = event.path("previousState").path("status").asText(null);
        if (com.yowyob.tiibntick.common.kafka.TntAggregateEvent.isAggregateEvent(event) && status != null
                && !status.equals(previous) && event.hasNonNull("tenantId")) {
            UUID tenantId = UUID.fromString(event.path("tenantId").asText());
            UUID deliveryId = UUID.fromString(event.path("aggregateId").asText());
            var mission = com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport.awaitBounded(
                    missionService.syncFromCoreStatus(tenantId, deliveryId, status, hintFrom(p)),
                    java.time.Duration.ofSeconds(30));
            if (mission != null) {
                log.debug("[DeliveryStatusConsumer] synced coreMissionId={} status={}", deliveryId, status);
            }
        }
        ack.acknowledge();
    }

    static CoreMissionSyncHint hintFrom(com.fasterxml.jackson.databind.JsonNode p) {
        return new CoreMissionSyncHint(
                uuidVal(text(p, "agencyId")), null,
                place(p.path("pickupAddress")), place(p.path("deliveryAddress")),
                null, text(p, "recipientDisplayName"), null,
                null, null, null, null,
                uuidVal(text(p, "relayHubId")));
    }

    /** "district, city" of a masked address (spec §3.4), null when absent. */
    private static String place(com.fasterxml.jackson.databind.JsonNode address) {
        String joined = java.util.stream.Stream.of(text(address, "district"), text(address, "city"))
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining(", "));
        return joined.isBlank() ? null : joined;
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode node, String field) {
        com.fasterxml.jackson.databind.JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static String stringVal(Object... candidates) {
        for (Object c : candidates) {
            if (c != null) {
                String s = c.toString();
                if (!s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static UUID uuidVal(Object... candidates) {
        String raw = stringVal(candidates);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
