package com.yowyob.tiibntick.core.realtime.application.service;

import com.yowyob.tiibntick.core.realtime.application.port.in.IWatchSubDeliverersUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.out.IPresenceRepository;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

/**
 * Application service implementing real-time sub-deliverer tracking for FreelancerOrgs.
 *
 * <p>Provides two streaming modes:
 * <ol>
 *   <li>Live stream: subscribes to Redis Pub/Sub channel
 *       {@code /topic/fleet/{freelancerOrgId}} and emits each incoming GPS ping.</li>
 *   <li>Snapshot: reads the last known position from the presence store for each
 *       sub-deliverer currently online in the org.</li>
 * </ol>
 *
 * <p>All GPS pings with {@code freelancerOrgId} set are automatically routed to the
 * fleet topic by {@link GpsPingApplicationService} — this service only handles the
 * consumer side.
 *
 * @author MANFOUO Braun
 */
@Service
public class WatchSubDeliverersApplicationService implements IWatchSubDeliverersUseCase {

    private static final Logger log = LoggerFactory.getLogger(WatchSubDeliverersApplicationService.class);

    private final IPresenceRepository presenceRepository;
    private final IWebSocketBroadcaster broadcaster;

    public WatchSubDeliverersApplicationService(IPresenceRepository presenceRepository,
                                                 IWebSocketBroadcaster broadcaster) {
        this.presenceRepository = presenceRepository;
        this.broadcaster = broadcaster;
    }

    @Override
    public Flux<GPSStreamEntry> watchSubDeliverers(String freelancerOrgId, String tenantId) {
        log.debug("Starting sub-deliverer watch for FreelancerOrg={} tenant={}", freelancerOrgId, tenantId);

        String fleetTopic = BroadcastTopic.forFreelancerOrgFleet(freelancerOrgId).path();

        // Subscribe to the fleet broadcast topic — each GPS ping from a sub-deliverer
        // is published here by GpsPingApplicationService when freelancerOrgId is set.
        return broadcaster.subscribeToTopic(fleetTopic)
                .flatMap(message -> {
                    try {
                        // The message is already a JSON-serialized GPSStreamEntry published by
                        // GpsPingApplicationService after processing the sub-deliverer's ping.
                        // Parse and emit it for the OWNER's dashboard.
                        if (message instanceof GPSStreamEntry gpsEntry) {
                            return reactor.core.publisher.Mono.just(gpsEntry);
                        }
                        // If raw message, return empty (format handled by concrete broadcaster impl)
                        return reactor.core.publisher.Mono.empty();
                    } catch (Exception e) {
                        log.warn("Failed to parse fleet GPS message for org={}: {}", freelancerOrgId, e.getMessage());
                        return reactor.core.publisher.Mono.empty();
                    }
                })
                .doOnSubscribe(s -> log.info("Owner watching fleet of FreelancerOrg={}", freelancerOrgId))
                .doOnCancel(() -> log.debug("Owner stopped watching fleet of FreelancerOrg={}", freelancerOrgId));
    }

    @Override
    public Flux<GPSStreamEntry> getLastKnownPositions(String freelancerOrgId, String tenantId) {
        log.debug("Getting last known positions for FreelancerOrg={}", freelancerOrgId);

        // Query the presence store for all active actors tagged with this org.
        // In production, this joins with the presence records that carry the freelancerOrgId.
        return presenceRepository.findOnlineActorsByOrg(freelancerOrgId, tenantId)
                .flatMap(presence -> {
                    // Reconstruct a synthetic GPS entry from the presence record
                    if (Double.isNaN(presence.getCurrentCoordinates().lastLat()) || Double.isNaN(presence.getCurrentCoordinates().lastLong())) {
                        return reactor.core.publisher.Mono.empty();
                    }
                    return reactor.core.publisher.Mono.just(new GPSStreamEntry(
                            presence.getUserId(),
                            presence.getActiveMissionId(),
                            tenantId,
                            GeoCoordinates.of(presence.getCurrentCoordinates().lastLat(), presence.getCurrentCoordinates().lastLong()),
                            0.0, 0.0, 0.0, null,
                            LocalDateTime.now(),
                            freelancerOrgId));
                });
    }
}
