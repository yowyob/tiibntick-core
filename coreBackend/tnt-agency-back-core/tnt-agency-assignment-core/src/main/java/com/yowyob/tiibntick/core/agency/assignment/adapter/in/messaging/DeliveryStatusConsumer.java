package com.yowyob.tiibntick.core.agency.assignment.adapter.in.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.agency.assignment.application.service.MissionService;
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
            sync(UUID.fromString(tenantIdStr), UUID.fromString(missionIdStr), "DELIVERED", ack);
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
            sync(UUID.fromString(tenantIdStr), UUID.fromString(missionIdStr), status, ack);
        } catch (Exception e) {
            log.warn("[DeliveryStatusConsumer] {} parse error: {}", kind, e.getMessage());
            ack.acknowledge();
        }
    }

    private void sync(UUID tenantId, UUID coreMissionId, String status, Acknowledgment ack) {
        missionService.syncFromCoreStatus(tenantId, coreMissionId, status)
                .doOnSuccess(m -> log.debug("[DeliveryStatusConsumer] synced coreMissionId={} status={}",
                        coreMissionId, status))
                .doOnError(e -> log.warn("[DeliveryStatusConsumer] sync failed coreMissionId={}: {}",
                        coreMissionId, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .doFinally(s -> ack.acknowledge())
                .subscribe();
    }

    private static String stringVal(Object... candidates) {
        for (Object c : candidates) {
            if (c != null) {
                return c.toString();
            }
        }
        return null;
    }
}
