package com.yowyob.tiibntick.core.realtime.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntAggregateEvent;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.realtime.application.port.in.IBroadcastNotificationUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import com.yowyob.tiibntick.core.realtime.domain.service.MissionTrackingCodeCache;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Pushes the delivery and parcel state changes to the WebSocket / SSE subscribers (lot L8, spec §3.7).
 *
 * <ul>
 *   <li>{@code tnt.delivery.events}: each status transition of a delivery → {@code /topic/delivery/{deliveryId}}
 *       ({@link MissionStatusPayload}, same fields as before: {@code missionId} is the delivery id,
 *       {@code deliveryPersonId} the courier);</li>
 *   <li>{@code tnt.parcel.events}: each status transition of a parcel → {@code /topic/tracking/{trackingCode}}
 *       ({@link TrackingStatusPayload}: code, parcel states, instant — the code is the parcel's, D-136/D-137); the
 *       parcel's current delivery is remembered for the GPS ETA ({@link MissionTrackingCodeCache}).</li>
 * </ul>
 * Records without {@code schemaVersion} and changes without a transition are ignored. The broadcast is awaited
 * (lot L7 rule): a failure is retried by the container, then dead-lettered to {@code tnt.dlq}.
 *
 * @author MANFOUO Braun
 */
@Component
public class MissionStatusEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MissionStatusEventConsumer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final IBroadcastNotificationUseCase broadcastNotification;
    private final ObjectMapper objectMapper;
    private final MissionTrackingCodeCache trackingCodeCache;

    public MissionStatusEventConsumer(IBroadcastNotificationUseCase broadcastNotification,
                                      ObjectMapper objectMapper,
                                      MissionTrackingCodeCache trackingCodeCache) {
        this.broadcastNotification = broadcastNotification;
        this.objectMapper = objectMapper;
        this.trackingCodeCache = trackingCodeCache;
    }

    @KafkaListener(
            topics = TntTopics.DELIVERY_EVENTS,
            groupId = "${tnt.kafka.consumer.groups.realtime:tnt-realtime-core}",
            containerFactory = "realtimeKafkaListenerContainerFactory"
    )
    public void onDeliveryEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
        JsonNode event = objectMapper.readTree(record.value());
        String status = transition(event);
        if (status != null) {
            JsonNode p = event.path("payload");
            String deliveryId = event.path("aggregateId").asText();
            MissionStatusPayload payload = new MissionStatusPayload(deliveryId, trackingCodeCache.get(deliveryId),
                    text(event, "tenantId"), text(event.path("previousState"), "status"), status,
                    text(p, "agencyId"), text(p, "courierId"), text(event, "occurredAt"));
            ReactiveListenerSupport.awaitBounded(broadcastNotification.broadcastToTopic(
                    BroadcastTopic.forDelivery(deliveryId).path(), payload).then(Mono.just(true)), TIMEOUT);
        }
        acknowledgment.acknowledge();
    }

    @KafkaListener(
            topics = TntTopics.PARCEL_EVENTS,
            groupId = "${tnt.kafka.consumer.groups.realtime:tnt-realtime-core}",
            containerFactory = "realtimeKafkaListenerContainerFactory"
    )
    public void onParcelEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
        JsonNode event = objectMapper.readTree(record.value());
        if (TntAggregateEvent.isAggregateEvent(event)) {
            JsonNode p = event.path("payload");
            String trackingCode = text(p, "trackingCode");
            trackingCodeCache.put(text(p, "currentDeliveryId"), trackingCode);
            String status = transition(event);
            if (status != null && trackingCode != null) {
                ReactiveListenerSupport.awaitBounded(broadcastNotification.broadcastToTopic(
                        BroadcastTopic.forTracking(trackingCode).path(),
                        new TrackingStatusPayload(trackingCode, text(event.path("previousState"), "status"), status,
                                text(event, "occurredAt"))).then(Mono.just(true)), TIMEOUT);
            }
        }
        acknowledgment.acknowledge();
    }

    /** @return the new status when the event is an aggregate event that changed it (creation included), else null */
    private static String transition(JsonNode event) {
        if (!TntAggregateEvent.isAggregateEvent(event)) {
            log.debug("Record without schemaVersion ignored (spec §3.9)");
            return null;
        }
        String status = text(event.path("payload"), "status");
        String previous = text(event.path("previousState"), "status");
        return status != null && !Objects.equals(status, previous) ? status : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /**
     * What the tracking-code topic receives (lot evt-l2, D-137): anyone holding the parcel code subscribes there, so
     * only the code, the parcel states and the instant — no tenant, agency or courier id (the delivery topic keeps
     * them).
     */
    record TrackingStatusPayload(String trackingCode, String previousStatus, String newStatus, String occurredAt) {
    }

    /** What the delivery topic receives (unchanged field names; {@code missionId} = delivery id). */
    record MissionStatusPayload(
            String missionId,
            String trackingCode,
            String tenantId,
            String previousStatus,
            String newStatus,
            String agencyId,
            String deliveryPersonId,
            String occurredAt
    ) {}
}
