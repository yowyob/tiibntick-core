package com.yowyob.tiibntick.core.gofreelancer.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntAggregateEvent;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryEventNotifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Inbound Kafka adapter: tells the people involved about every status transition of a delivery (see
 * {@link DeliveryEventNotifier}), read from {@code tnt.delivery.events} (lot L8, was
 * {@code tnt.delivery.mission.status.changed}). Changes without a transition and legacy records are ignored. The
 * delivery snapshot has no tracking code: it is read from the core for the statuses that are notified.
 *
 * <p>It no longer copies the status onto a gofp row: the delivery's status is owned by the core and read from it.
 * The consumer group keeps its historical id. The work is awaited (lot L7 rule): a failure is retried, then
 * dead-lettered to {@code tnt.dlq}.
 */
@Slf4j
@Component
public class CoreMissionStatusConsumer {

    private final DeliveryEventNotifier notifier;
    private final IDeliveryLifecyclePort lifecycle;
    private final ObjectMapper objectMapper;
    private final ReactiveListenerSupport listenerSupport;

    public CoreMissionStatusConsumer(DeliveryEventNotifier notifier, IDeliveryLifecyclePort lifecycle,
                                     @Qualifier("tntObjectMapper") ObjectMapper objectMapper,
                                     ObjectProvider<ReactiveListenerSupport> listenerSupport) {
        this.notifier = notifier;
        this.lifecycle = lifecycle;
        this.objectMapper = objectMapper;
        this.listenerSupport = listenerSupport.getIfAvailable(ReactiveListenerSupport::withDefaultTimeout);
    }

    @KafkaListener(
            topics = TntTopics.DELIVERY_EVENTS,
            groupId = "tnt-gofp-delivery-projection",
            containerFactory = "gofpKafkaListenerContainerFactory")
    public void onDeliveryStatusChanged(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        JsonNode event = objectMapper.readTree(record.value());
        String newStatus = event.path("payload").path("status").asText(null);
        if (TntAggregateEvent.isAggregateEvent(event) && newStatus != null
                && !Objects.equals(newStatus, event.path("previousState").path("status").asText(null))
                && DeliveryEventNotifier.isNotified(newStatus)) {
            UUID deliveryId = UUID.fromString(event.path("aggregateId").asText());
            UUID eventId = UUID.fromString(event.path("eventId").asText());
            UUID tenantId = event.hasNonNull("tenantId") ? UUID.fromString(event.path("tenantId").asText()) : null;
            // empty when the core does not know the delivery (no NotFound error); any other lookup failure reaches
            // the container (retries, then tnt.dlq — G.8), the notifier deduping by eventId
            Mono<String> trackingCode = tenantId == null ? Mono.empty()
                    : lifecycle.findStateAnyTenant(deliveryId)
                            .mapNotNull(IDeliveryLifecyclePort.CoreDeliveryState::trackingCode);
            Integer sent = listenerSupport.await(trackingCode.map(java.util.Optional::of)
                    .defaultIfEmpty(java.util.Optional.empty())
                    .flatMap(code -> notifier.onDeliveryStatusChanged(eventId, deliveryId, tenantId, newStatus,
                            code.orElse(null))));
            log.debug("Delivery {} {}: {} notification(s)", deliveryId, newStatus, sent);
        }
        ack.acknowledge();
    }
}
