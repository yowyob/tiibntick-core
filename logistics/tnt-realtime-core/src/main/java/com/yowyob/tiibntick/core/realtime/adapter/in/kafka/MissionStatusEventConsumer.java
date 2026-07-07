package com.yowyob.tiibntick.core.realtime.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.realtime.application.port.in.IBroadcastNotificationUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/**
 * Kafka consumer for mission status change events from tnt-delivery-core.
 *
 * <p>Listens on {@code tnt.delivery.mission.status.changed} — emitted by
 * tnt-delivery-core when a mission transitions between states (e.g. ASSIGNED →
 * PICKED_UP → IN_TRANSIT → DELIVERED).</p>
 *
 * <p>Broadcasts the status change to all WebSocket clients subscribed to:</p>
 * <ul>
 *   <li>{@code /topic/delivery/{missionId}} — for mission-level observers</li>
 *   <li>{@code /topic/tracking/{trackingCode}} — for package tracking observers</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Component
public class MissionStatusEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MissionStatusEventConsumer.class);

    private final IBroadcastNotificationUseCase broadcastNotification;
    private final ObjectMapper objectMapper;

    public MissionStatusEventConsumer(IBroadcastNotificationUseCase broadcastNotification,
                                      ObjectMapper objectMapper) {
        this.broadcastNotification = broadcastNotification;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes mission status events and broadcasts them to subscribed WebSocket clients.
     *
     * @param record         the Kafka consumer record
     * @param acknowledgment manual Kafka acknowledgment
     */
    @KafkaListener(
            topics = "${tnt.realtime.kafka.topics.mission-status-changed:tnt.delivery.mission.status.changed}",
            groupId = "${spring.kafka.consumer.group-id:tnt-realtime-core}",
            containerFactory = "realtimeKafkaListenerContainerFactory"
    )
    public void onMissionStatusChanged(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.debug("Received mission status event from tnt-delivery-core (key={})", record.key());

        try {
            MissionStatusPayload payload = objectMapper.readValue(record.value(), MissionStatusPayload.class);

            String deliveryTopic = BroadcastTopic.forDelivery(payload.missionId()).path();
            String trackingTopic = payload.trackingCode() != null
                    ? BroadcastTopic.forTracking(payload.trackingCode()).path() : null;

            broadcastNotification.broadcastToTopic(deliveryTopic, payload)
                    .then(trackingTopic != null
                            ? broadcastNotification.broadcastToTopic(trackingTopic, payload)
                            : reactor.core.publisher.Mono.empty())
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnSuccess(v -> acknowledgment.acknowledge())
                    .doOnError(ex -> {
                        log.error("Failed to broadcast mission status for {}: {}", payload.missionId(), ex.getMessage());
                        acknowledgment.acknowledge();
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("Failed to deserialize mission status event: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    /**
     * Internal DTO for mission status change events from tnt-delivery-core.
     */
    record MissionStatusPayload(
            String missionId,
            String trackingCode,
            String tenantId,
            String previousStatus,
            String newStatus,
            String agencyId,
            String delivererId,
            String occurredAt
    ) {}
}
