package com.yowyob.tiibntick.core.agency.assignment.adapter.in.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
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
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

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
            topics = "${tnt.kafka.topics.consumed.core-mission-status}",
            groupId = "${tnt.kafka.topics.groups.agency-erp-delivery:tnt-agency-erp-delivery-consumer}",
            containerFactory = "agencyAssignmentKafkaListenerFactory"
    )
    public void onMissionStatusChanged(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack, "status-changed");
    }

    @KafkaListener(
            topics = "${tnt.kafka.topics.consumed.core-mission-created}",
            groupId = "${tnt.kafka.topics.groups.agency-erp-delivery:tnt-agency-erp-delivery-consumer}",
            containerFactory = "agencyAssignmentKafkaListenerFactory"
    )
    public void onMissionCreated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack, "created");
    }

    @KafkaListener(
            topics = "${tnt.kafka.topics.consumed.core-package-delivered}",
            groupId = "${tnt.kafka.topics.groups.agency-erp-delivery:tnt-agency-erp-delivery-consumer}",
            containerFactory = "agencyAssignmentKafkaListenerFactory"
    )
    public void onPackageDelivered(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});
            String missionIdStr = stringVal(payload.get("missionId"), payload.get("coreMissionId"));
            String tenantIdStr = stringVal(payload.get("tenantId"));
            if (missionIdStr == null || tenantIdStr == null) {
                ack.acknowledge();
                return;
            }
            sync(UUID.fromString(tenantIdStr), UUID.fromString(missionIdStr), "DELIVERED",
                    hintFrom(payload), ack);
        } catch (Exception e) {
            log.warn("[DeliveryStatusConsumer] package.delivered parse error: {}", e.getMessage());
            ack.acknowledge();
        }
    }

    private void handle(ConsumerRecord<String, String> record, Acknowledgment ack, String kind) {
        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});
            String missionIdStr = stringVal(payload.get("missionId"), payload.get("coreMissionId"));
            String tenantIdStr = stringVal(payload.get("tenantId"));
            if (missionIdStr == null || tenantIdStr == null) {
                ack.acknowledge();
                return;
            }
            String status = stringVal(payload.get("status"), payload.get("newStatus"));
            if (status == null && "created".equals(kind)) {
                status = "CREATED";
            }
            if (status == null) {
                ack.acknowledge();
                return;
            }
            sync(UUID.fromString(tenantIdStr), UUID.fromString(missionIdStr), status,
                    hintFrom(payload), ack);
        } catch (Exception e) {
            log.warn("[DeliveryStatusConsumer] {} parse error: {}", kind, e.getMessage());
            ack.acknowledge();
        }
    }

    private void sync(UUID tenantId, UUID coreMissionId, String status,
                      CoreMissionSyncHint hint, Acknowledgment ack) {
        missionService.syncFromCoreStatus(tenantId, coreMissionId, status, hint)
                .doOnSuccess(m -> {
                    if (m != null) {
                        log.debug("[DeliveryStatusConsumer] synced coreMissionId={} status={}",
                                coreMissionId, status);
                    }
                })
                .doOnError(e -> log.warn("[DeliveryStatusConsumer] sync failed coreMissionId={}: {}",
                        coreMissionId, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .doFinally(s -> ack.acknowledge())
                .subscribe();
    }

    private static CoreMissionSyncHint hintFrom(Map<String, Object> payload) {
        return new CoreMissionSyncHint(
                uuidVal(payload.get("agencyId"), payload.get("agency_id")),
                uuidVal(payload.get("branchId"), payload.get("branch_id")),
                stringVal(payload.get("pickupAddress"), payload.get("pickup_address")),
                stringVal(payload.get("deliveryAddress"), payload.get("delivery_address")),
                stringVal(payload.get("senderName"), payload.get("sender_name")),
                stringVal(payload.get("recipientName"), payload.get("recipient_name")),
                stringVal(payload.get("recipientPhone"), payload.get("recipient_phone")),
                doubleVal(payload.get("weightKg"), payload.get("weight_kg")),
                doubleVal(payload.get("distanceKm"), payload.get("distance_km")),
                intVal(payload.get("packagesCount"), payload.get("packages_count")),
                stringVal(payload.get("priority")),
                uuidVal(payload.get("targetHubId"), payload.get("target_hub_id"), payload.get("relayPointId"))
        );
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

    private static Double doubleVal(Object... candidates) {
        for (Object c : candidates) {
            if (c instanceof Number n) {
                return n.doubleValue();
            }
            if (c != null) {
                try {
                    return Double.parseDouble(c.toString());
                } catch (NumberFormatException ignored) {
                    // next
                }
            }
        }
        return null;
    }

    private static Integer intVal(Object... candidates) {
        for (Object c : candidates) {
            if (c instanceof Number n) {
                return n.intValue();
            }
            if (c != null) {
                try {
                    return Integer.parseInt(c.toString());
                } catch (NumberFormatException ignored) {
                    // next
                }
            }
        }
        return null;
    }
}
