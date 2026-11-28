package com.yowyob.tiibntick.core.delivery.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Clears the delivery thresholds cache when an administrator changes a threshold ({@code DeliveryThresholdsChanged}
 * on {@code tnt.administration.events}, lot L1). Every instance must see the event: the consumer group is unique per
 * instance and starts at the latest offset (the cache is empty at start-up).
 */
@Slf4j
@Component
public class DeliveryThresholdsChangedConsumer {

    static final String EVENT_TYPE = "DeliveryThresholdsChanged";

    private final DeliveryThresholdsPort thresholds;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeliveryThresholdsChangedConsumer(DeliveryThresholdsPort thresholds) {
        this.thresholds = thresholds;
    }

    @KafkaListener(
            topics = TntTopics.ADMINISTRATION_EVENTS,
            groupId = "tnt-delivery-thresholds-cache-${random.uuid}",
            containerFactory = "deliveryKafkaListenerContainerFactory",
            properties = {"auto.offset.reset=latest"})
    public void onAdministrationEvent(ConsumerRecord<String, String> record) {
        if (isThresholdsChange(record.value())) {
            thresholds.invalidateAll();
            log.info("Delivery thresholds changed (key={}): cache cleared", record.key());
        }
    }

    boolean isThresholdsChange(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(body);
            return EVENT_TYPE.equals(root.path("eventType").asText(null));
        } catch (Exception e) {
            log.debug("Ignoring unreadable administration event: {}", e.getMessage());
            return false;
        }
    }
}
