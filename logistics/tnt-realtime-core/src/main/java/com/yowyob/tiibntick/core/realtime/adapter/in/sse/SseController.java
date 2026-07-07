package com.yowyob.tiibntick.core.realtime.adapter.in.sse;

import com.yowyob.tiibntick.core.realtime.application.port.in.IWatchSubDeliverersUseCase;
import com.yowyob.tiibntick.core.realtime.domain.service.SseDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.UUID;

/**
 * SSE (Server-Sent Events) controller providing real-time delivery tracking
 * for lightweight HTTP clients that cannot use WebSocket.
 *
 * <p>Clients subscribe to a tracking code stream and receive ETA updates,
 * status changes, and delivery events as SSE messages.</p>
 *
 * <p>Each SSE connection also sends a periodic heartbeat (comment frame)
 * every 15 seconds to keep the connection alive through proxies.</p>
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/realtime/sse")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final SseDomainService sseDomainService;
    private final IWatchSubDeliverersUseCase watchSubDeliverersUseCase;

    public SseController(SseDomainService sseDomainService,
                         IWatchSubDeliverersUseCase watchSubDeliverersUseCase) {
        this.sseDomainService = sseDomainService;
        this.watchSubDeliverersUseCase = watchSubDeliverersUseCase;
    }

    /**
     * Opens an SSE stream for tracking a specific package by its tracking code.
     * Any authenticated user with the tracking code can subscribe.
     *
     * <p>Stream produces:</p>
     * <ul>
     *   <li>{@code event: eta-update} — live ETA update JSON</li>
     *   <li>{@code event: status-change} — delivery status change</li>
     *   <li>{@code event: heartbeat} — keep-alive comment every 15s</li>
     * </ul>
     *
     * @param trackingCode the package tracking code (e.g. TNT-DEL-000042)
     * @param tenantId     the tenant context (extracted from request header)
     * @return Flux of SSE events
     */
    @GetMapping(value = "/tracking/{trackingCode}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> trackPackage(
            @PathVariable String trackingCode,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "X-Tenant-Id", defaultValue = "default") String tenantId) {

        log.debug("SSE tracking stream opened for code {} (tenant {})", trackingCode, tenantId);

        Flux<ServerSentEvent<String>> dataStream = sseDomainService
                .trackingStream(trackingCode, tenantId)
                .map(json -> ServerSentEvent.<String>builder()
                        .id(UUID.randomUUID().toString())
                        .event("eta-update")
                        .data(json)
                        .build());

        Flux<ServerSentEvent<String>> heartbeatStream = Flux.interval(HEARTBEAT_INTERVAL)
                .map(tick -> ServerSentEvent.<String>builder()
                        .comment("heartbeat")
                        .build());

        return Flux.merge(dataStream, heartbeatStream)
                .doOnCancel(() -> log.debug("SSE tracking stream closed for code {}", trackingCode))
                .doOnError(ex -> log.warn("SSE tracking stream error for code {}: {}", trackingCode, ex.getMessage()));
    }

    /**
     * Opens an SSE stream for monitoring a delivery mission (agency/dispatcher view).
     * Requires an authenticated session with the appropriate role.
     *
     * @param missionId the mission identifier
     * @param tenantId  the tenant context
     * @return Flux of SSE events
     */
    @GetMapping(value = "/mission/{missionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> trackMission(
            @PathVariable String missionId,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "X-Tenant-Id", defaultValue = "default") String tenantId) {

        log.debug("SSE mission stream opened for mission {} (tenant {})", missionId, tenantId);

        Flux<ServerSentEvent<String>> dataStream = sseDomainService
                .missionStream(missionId, tenantId)
                .map(json -> ServerSentEvent.<String>builder()
                        .id(UUID.randomUUID().toString())
                        .event("mission-update")
                        .data(json)
                        .build());

        Flux<ServerSentEvent<String>> heartbeatStream = Flux.interval(HEARTBEAT_INTERVAL)
                .map(tick -> ServerSentEvent.<String>builder()
                        .comment("heartbeat")
                        .build());

        return Flux.merge(dataStream, heartbeatStream)
                .doOnCancel(() -> log.debug("SSE mission stream closed for mission {}", missionId));
    }
    // ── : FreelancerOrg sub-deliverer fleet tracking ──────────────────────

    /**
     * Server-Sent Events stream: live GPS positions of all sub-deliverers
     * in a FreelancerOrganization's fleet.
     *
     * <p>Designed for the FreelancerOrg OWNER's web/mobile dashboard.
     * Emits a GPS position event every time any sub-deliverer sends a ping.</p>
     *
     * <p>Endpoint: {@code GET /realtime/sse/fleet/{freelancerOrgId}}</p>
     *
     * @param freelancerOrgId  the FreelancerOrg UUID (from tnt-organization-core)
     * @param tenantId         tenant identifier (required)
     */
    @org.springframework.web.bind.annotation.GetMapping(
            value = "/fleet/{freelancerOrgId}",
            produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public reactor.core.publisher.Flux<org.springframework.http.codec.ServerSentEvent<Object>>
            streamFreelancerOrgFleet(
                    @org.springframework.web.bind.annotation.PathVariable String freelancerOrgId,
                    @org.springframework.web.bind.annotation.RequestParam String tenantId) {

        log.info("SSE fleet stream opened for FreelancerOrg={} tenant={}", freelancerOrgId, tenantId);
        return watchSubDeliverersUseCase.watchSubDeliverers(freelancerOrgId, tenantId)
                .map(gpsEntry -> org.springframework.http.codec.ServerSentEvent.builder()
                        .event("fleet-gps")
                        .data((Object) gpsEntry)
                        .id(gpsEntry.delivererId() + ":" + gpsEntry.timestamp())
                        .build())
                .doOnCancel(() -> log.debug("SSE fleet stream closed for FreelancerOrg={}", freelancerOrgId));
    }

}