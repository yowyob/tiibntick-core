package com.yowyob.tiibntick.core.realtime.domain.service;

import com.yowyob.tiibntick.core.realtime.application.port.out.ISseEmitter;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Domain service managing Server-Sent Events (SSE) streams for lightweight clients
 * that cannot use WebSocket (e.g. simple browser clients behind restrictive proxies).
 *
 * <p>SSE provides a one-directional, server-to-client channel for real-time updates.
 * Use-cases: public tracking page, customer delivery status widget.</p>
 *
 * @author MANFOUO Braun
 */
public class SseDomainService {

    private static final Logger log = LoggerFactory.getLogger(SseDomainService.class);

    private final ISseEmitter sseEmitter;

    public SseDomainService(ISseEmitter sseEmitter) {
        this.sseEmitter = sseEmitter;
    }

    /**
     * Creates an SSE stream for a client tracking a specific package.
     * The stream emits ETA updates and status changes until the connection is closed.
     *
     * @param trackingCode the tracking code to follow
     * @param tenantId     the tenant context
     * @return Flux of SSE events (serialized as JSON strings)
     */
    public Flux<String> trackingStream(String trackingCode, String tenantId) {
        BroadcastTopic topic = BroadcastTopic.forTracking(trackingCode);
        log.debug("Opening SSE tracking stream for code {} (tenant {})", trackingCode, tenantId);
        return sseEmitter.subscribe(topic);
    }

    /**
     * Creates an SSE stream for a client monitoring a specific delivery mission.
     *
     * @param missionId the mission identifier
     * @param tenantId  the tenant context
     * @return Flux of SSE events
     */
    public Flux<String> missionStream(String missionId, String tenantId) {
        BroadcastTopic topic = BroadcastTopic.forDelivery(missionId);
        log.debug("Opening SSE mission stream for mission {} (tenant {})", missionId, tenantId);
        return sseEmitter.subscribe(topic);
    }

    /**
     * Publishes an ETA update directly to SSE subscribers for a tracking code.
     * Called after WebSocket broadcast to also reach SSE clients.
     *
     * @param etaUpdate the ETA update to publish
     * @return Mono completing when published
     */
    public Mono<Void> publishEtaToSse(LiveETAUpdate etaUpdate) {
        if (etaUpdate.trackingCode() == null) {
            return Mono.empty();
        }
        BroadcastTopic topic = BroadcastTopic.forTracking(etaUpdate.trackingCode());
        return sseEmitter.emit(topic, etaUpdate);
    }
}
