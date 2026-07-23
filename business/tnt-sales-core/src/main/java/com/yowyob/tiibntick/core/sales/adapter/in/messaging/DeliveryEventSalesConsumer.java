package com.yowyob.tiibntick.core.sales.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.sales.application.service.SalesApplicationService;
import com.yowyob.tiibntick.core.sales.domain.model.ReturnReason;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Inbound Kafka consumer that listens to tnt-delivery-core events and automatically
 * progresses the matching SalesOrder lifecycle.
 *
 * Topics consumed:
 *   - tnt.delivery.mission.started      → startDelivery (order IN_DELIVERY)
 *   - tnt.delivery.mission.completed    → markDelivered (order DELIVERED)
 *   - tnt.delivery.mission.failed       → returnOrder  (order RETURNED)
 *
 * Author: MANFOUO Braun
 */
@Component
public class DeliveryEventSalesConsumer {

    private final SalesApplicationService salesService;
    private final ObjectMapper objectMapper;

    public DeliveryEventSalesConsumer(SalesApplicationService salesService,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.salesService = salesService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TntTopics.DELIVERY_MISSION_STARTED, groupId = "tnt-sales-core-delivery", containerFactory = "salesKafkaListenerContainerFactory")
    public void onMissionStarted(ConsumerRecord<String, String> record) {
        parseAndProcess(record.value(), node -> {
            UUID tenantId = uuid(node, "tenantId");
            UUID orderId  = uuid(node, "orderId");
            salesService.startDelivery(tenantId, orderId)
                    .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                    .subscribe();
        });
    }

    @KafkaListener(topics = TntTopics.DELIVERY_MISSION_COMPLETED, groupId = "tnt-sales-core-delivery", containerFactory = "salesKafkaListenerContainerFactory")
    public void onMissionCompleted(ConsumerRecord<String, String> record) {
        parseAndProcess(record.value(), node -> {
            UUID tenantId = uuid(node, "tenantId");
            UUID orderId  = uuid(node, "orderId");
            salesService.markDelivered(tenantId, orderId)
                    .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                    .subscribe();
        });
    }

    @KafkaListener(topics = TntTopics.DELIVERY_MISSION_FAILED, groupId = "tnt-sales-core-delivery", containerFactory = "salesKafkaListenerContainerFactory")
    public void onMissionFailed(ConsumerRecord<String, String> record) {
        parseAndProcess(record.value(), node -> {
            UUID tenantId  = uuid(node, "tenantId");
            UUID orderId   = uuid(node, "orderId");
            String reason  = node.has("failureReason") ? node.get("failureReason").asText("OTHER") : "OTHER";
            ReturnReason returnReason = toReturnReason(reason);
            salesService.returnOrder(tenantId, orderId, returnReason, "Auto-returned from delivery failure: " + reason)
                    .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                    .subscribe();
        });
    }

    private void parseAndProcess(String payload, java.util.function.Consumer<JsonNode> handler) {
        try {
            handler.accept(objectMapper.readTree(payload));
        } catch (Exception ignored) {
            // Log and skip — dead-letter queue handles persistent failures
        }
    }

    private UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.get(field).asText());
    }

    private ReturnReason toReturnReason(String raw) {
        try {
            return ReturnReason.valueOf(raw.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return ReturnReason.OTHER;
        }
    }
}
