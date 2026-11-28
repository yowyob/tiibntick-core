package com.yowyob.tiibntick.core.gofreelancer.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntAggregateEvent;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryEventNotifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@code DeliveryAlertRaised(DELIVERY_LATE)} of {@code tnt.delivery.events} → "delivery late" notification (lot L3,
 * QO-18). Other event types of the topic and legacy records are ignored. Blocking (lot L7 rule).
 */
@Slf4j
@Component
public class CoreDeliveryAlertConsumer {

    static final String ALERT_RAISED = "DeliveryAlertRaised";
    static final String DELIVERY_LATE = "DELIVERY_LATE";

    private final DeliveryEventNotifier notifier;
    private final ObjectMapper objectMapper;
    private final ReactiveListenerSupport listenerSupport;

    public CoreDeliveryAlertConsumer(DeliveryEventNotifier notifier,
                                     @Qualifier("tntObjectMapper") ObjectMapper objectMapper,
                                     ObjectProvider<ReactiveListenerSupport> listenerSupport) {
        this.notifier = notifier;
        this.objectMapper = objectMapper;
        this.listenerSupport = listenerSupport.getIfAvailable(ReactiveListenerSupport::withDefaultTimeout);
    }

    @KafkaListener(
            topics = TntTopics.DELIVERY_EVENTS,
            groupId = "tnt-gofp-delivery-alerts",
            containerFactory = "gofpJsonKafkaListenerContainerFactory")
    public void onDeliveryEvent(ConsumerRecord<String, String> record) throws Exception {
        JsonNode root = objectMapper.readTree(record.value());
        if (!TntAggregateEvent.isAggregateEvent(root) || !ALERT_RAISED.equals(root.path("eventType").asText())
                || !DELIVERY_LATE.equals(root.path("details").path("alertType").asText())) {
            return;
        }
        UUID eventId = UUID.fromString(root.path("eventId").asText());
        UUID deliveryId = UUID.fromString(root.path("aggregateId").asText());
        String tenant = root.path("tenantId").asText(null);
        UUID tenantId = tenant == null || tenant.isBlank() ? null : UUID.fromString(tenant);
        String trackingCode = root.path("payload").path("trackingCode").asText(null);
        Integer sent = listenerSupport.await(notifier.onDeliveryLate(eventId, deliveryId, tenantId, trackingCode));
        log.debug("Delivery {} late: {} notification(s)", deliveryId, sent);
    }
}
