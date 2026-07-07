package com.yowyob.tiibntick.core.realtime.domain.service;

import com.yowyob.tiibntick.core.realtime.application.port.out.IRealtimeEventPublisher;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.event.ETAUpdatedEvent;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;
import com.yowyob.tiibntick.core.realtime.domain.model.ReroutingAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Domain service responsible for broadcasting ETA and rerouting updates
 * to all interested WebSocket subscribers.
 *
 * <p>This service is called both from the GPS ping pipeline (direct)
 * and from Kafka consumers (when tnt-route-core publishes ETA updates
 * after a batch Kalman pass).</p>
 *
 * @author MANFOUO Braun
 */
public class EtaBroadcastDomainService {

    private static final Logger log = LoggerFactory.getLogger(EtaBroadcastDomainService.class);

    private final IWebSocketBroadcaster broadcaster;
    private final IRealtimeEventPublisher eventPublisher;

    public EtaBroadcastDomainService(IWebSocketBroadcaster broadcaster,
                                     IRealtimeEventPublisher eventPublisher) {
        this.broadcaster = broadcaster;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Broadcasts a live ETA update to all relevant topics and publishes the
     * corresponding Kafka event.
     *
     * <p>Broadcasts to:</p>
     * <ul>
     *   <li>{@code /topic/delivery/{missionId}} — mission-level subscribers</li>
     *   <li>{@code /topic/tracking/{trackingCode}} — public tracking subscribers</li>
     * </ul>
     *
     * @param etaUpdate the ETA update to broadcast
     * @return Mono completing when all broadcasts are dispatched
     */
    public Mono<Void> broadcastEta(LiveETAUpdate etaUpdate) {
        log.debug("Broadcasting ETA update for mission {} — ETA: {}", etaUpdate.missionId(), etaUpdate.bestEta());

        BroadcastTopic deliveryTopic = BroadcastTopic.forDelivery(etaUpdate.missionId());
        ETAUpdatedEvent event = new ETAUpdatedEvent(etaUpdate.tenantId(), etaUpdate);

        Mono<Void> deliveryBroadcast = broadcaster.broadcast(deliveryTopic, etaUpdate);

        Mono<Void> trackingBroadcast = etaUpdate.trackingCode() != null
                ? broadcaster.broadcast(BroadcastTopic.forTracking(etaUpdate.trackingCode()), etaUpdate)
                : Mono.empty();

        Mono<Void> kafkaPublish = eventPublisher.publish(event)
                .onErrorResume(ex -> {
                    log.warn("Failed to publish ETAUpdatedEvent to Kafka: {}", ex.getMessage());
                    return Mono.empty();
                });

        return Mono.when(deliveryBroadcast, trackingBroadcast, kafkaPublish);
    }

    /**
     * Broadcasts a rerouting alert to the assigned deliverer.
     * Also broadcasts to the agency topic for dispatcher awareness.
     *
     * @param alert the rerouting alert
     * @return Mono completing when the broadcast is dispatched
     */
    public Mono<Void> broadcastRerouting(ReroutingAlert alert) {
        log.info("Broadcasting rerouting alert for mission {} — improvement: {}%",
                alert.missionId(), String.format("%.1f", alert.costImprovement() * 100));

        BroadcastTopic rerouteTopic = BroadcastTopic.forReroute(alert.missionId());
        BroadcastTopic delivererTopic = BroadcastTopic.forUser(alert.delivererId());

        return Mono.when(
                broadcaster.broadcast(rerouteTopic, alert),
                broadcaster.broadcast(delivererTopic, alert)
        ).onErrorResume(ex -> {
            log.warn("Failed to broadcast rerouting alert for mission {}: {}", alert.missionId(), ex.getMessage());
            return Mono.empty();
        });
    }
}
