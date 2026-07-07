package com.yowyob.tiibntick.core.realtime.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.realtime.application.port.in.IBroadcastEtaUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.ETAInterval;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

/**
 * Kafka consumer for ETA update events published by tnt-route-core.
 *
 * <p>Listens on {@code tnt.route.eta.updated} — emitted by tnt-route-core
 * after the Kalman filter processes a batch of GPS readings or when the
 * VRP solver produces a new optimal route.</p>
 *
 * <p>Upon receipt, broadcasts the ETA update via WebSocket to all clients
 * subscribed to {@code /topic/delivery/{missionId}} and triggers SSE emission.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class ETAUpdateEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ETAUpdateEventConsumer.class);

    private final IBroadcastEtaUseCase broadcastEtaUseCase;
    private final ObjectMapper objectMapper;

    public ETAUpdateEventConsumer(IBroadcastEtaUseCase broadcastEtaUseCase,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.broadcastEtaUseCase = broadcastEtaUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes ETA update events from tnt-route-core and broadcasts them.
     *
     * @param record         the Kafka consumer record
     * @param acknowledgment manual Kafka acknowledgment
     */
    @KafkaListener(
            topics = "${tnt.realtime.kafka.topics.eta-updated:tnt.route.eta.updated}",
            groupId = "${spring.kafka.consumer.group-id:tnt-realtime-core}",
            containerFactory = "realtimeKafkaListenerContainerFactory"
    )
    public void onEtaUpdated(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.debug("Received ETA update event from tnt-route-core (key={})", record.key());

        try {
            ETAUpdatePayload payload = objectMapper.readValue(record.value(), ETAUpdatePayload.class);

            LiveETAUpdate etaUpdate = LiveETAUpdate.of(
                    payload.missionId(),
                    payload.delivererId(),
                    payload.tenantId(),
                    payload.trackingCode(),
                    GeoCoordinates.of(payload.currentLatitude(), payload.currentLongitude()),
                    ETAInterval.of(
                            LocalDateTime.parse(payload.etaLowerBound()),
                            LocalDateTime.parse(payload.etaUpperBound()),
                            payload.kalmanConfidence()
                    ),
                    payload.remainingDistanceKm(),
                    payload.remainingTimeMin(),
                    payload.kalmanConfidence()
            );

            broadcastEtaUseCase.broadcastEtaUpdate(etaUpdate)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnSuccess(v -> acknowledgment.acknowledge())
                    .doOnError(ex -> {
                        log.error("Failed to broadcast ETA update for mission {}: {}",
                                payload.missionId(), ex.getMessage());
                        acknowledgment.acknowledge(); // Acknowledge even on error to avoid reprocessing
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("Failed to deserialize ETA update event: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    /**
     * Internal DTO for deserializing ETA update events from tnt-route-core.
     */
    record ETAUpdatePayload(
            String missionId,
            String delivererId,
            String tenantId,
            String trackingCode,
            double currentLatitude,
            double currentLongitude,
            String etaLowerBound,
            String etaUpperBound,
            double kalmanConfidence,
            double remainingDistanceKm,
            int remainingTimeMin
    ) {}
    /**
     * Internal payload record extended for  FreelancerOrg context.
     */
    record ETAPayload(String missionId, String trackingCode, int etaMinutes,
                      String status, String vehicleType,
                      Double currentLat, Double currentLng,
                      String freelancerOrgId) {}

}