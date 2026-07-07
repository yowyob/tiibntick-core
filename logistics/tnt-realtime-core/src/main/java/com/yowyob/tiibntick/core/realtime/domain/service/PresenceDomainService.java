package com.yowyob.tiibntick.core.realtime.domain.service;

import com.yowyob.tiibntick.core.realtime.application.port.out.IPresenceRepository;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import com.yowyob.tiibntick.core.realtime.domain.model.DeviceInfo;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.PresenceRecord;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.PresenceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Domain service managing actor presence lifecycle.
 *
 * <p>Presence records are persisted in Redis with a configurable TTL (default 30s).
 * If no keepalive (GPS ping or heartbeat) arrives within the TTL, Redis automatically
 * deletes the record and the actor is treated as offline.</p>
 *
 * <p>When presence changes, the new state is broadcast via WebSocket to the
 * tenant-wide presence board ({@code /topic/presence/{tenantId}}).</p>
 *
 * @author MANFOUO Braun
 */
public class PresenceDomainService {

    private static final Logger log = LoggerFactory.getLogger(PresenceDomainService.class);

    private final IPresenceRepository presenceRepository;
    private final IWebSocketBroadcaster broadcaster;

    public PresenceDomainService(IPresenceRepository presenceRepository,
                                 IWebSocketBroadcaster broadcaster) {
        this.presenceRepository = presenceRepository;
        this.broadcaster = broadcaster;
    }

    /**
     * Marks an actor as online when they establish a WebSocket connection.
     * Creates or refreshes their presence record in Redis.
     *
     * @param userId     the actor's user identifier
     * @param tenantId   the tenant context
     * @param deviceInfo device metadata from the CONNECT frame
     * @return Mono completing after persistence and broadcast
     */
    public Mono<Void> markOnline(String userId, String tenantId, DeviceInfo deviceInfo) {
        PresenceRecord record = new PresenceRecord(userId, tenantId, deviceInfo);
        return presenceRepository.save(record)
                .then(broadcastPresenceChange(record))
                .doOnSuccess(v -> log.debug("Actor {} marked ONLINE (tenant {})", userId, tenantId));
    }

    /**
     * Marks an actor as offline when their session disconnects.
     * Updates Redis and broadcasts to the presence board.
     *
     * @param userId   the actor's user identifier
     * @param tenantId the tenant context
     * @return Mono completing after persistence and broadcast
     */
    public Mono<Void> markOffline(String userId, String tenantId) {
        return presenceRepository.findByUserAndTenant(userId, tenantId)
                .flatMap(record -> {
                    record.markOffline();
                    return presenceRepository.save(record)
                            .then(broadcastPresenceChange(record));
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.debug("No presence record found for actor {} (tenant {}) on disconnect", userId, tenantId)))
                .doOnSuccess(v -> log.debug("Actor {} marked OFFLINE (tenant {})", userId, tenantId));
    }

    /**
     * Updates an actor's GPS coordinates in their presence record.
     * Also refreshes the Redis TTL.
     *
     * @param userId      the actor's user identifier
     * @param tenantId    the tenant context
     * @param coordinates the new GPS coordinates
     * @return Mono completing after persistence
     */
    public Mono<Void> updateCoordinates(String userId, String tenantId, GeoCoordinates coordinates) {
        return presenceRepository.findByUserAndTenant(userId, tenantId)
                .flatMap(record -> {
                    record.updateLocation(coordinates);
                    return presenceRepository.save(record);
                })
                .switchIfEmpty(Mono.empty())
                .then();
    }

    /**
     * Assigns an active mission to an actor, transitioning them to ONLINE_ON_MISSION.
     *
     * @param userId     the actor's user identifier
     * @param tenantId   the tenant context
     * @param missionId  the mission to assign
     * @return Mono completing after persistence and broadcast
     */
    public Mono<Void> assignMission(String userId, String tenantId, String missionId) {
        return presenceRepository.findByUserAndTenant(userId, tenantId)
                .flatMap(record -> {
                    record.assignMission(missionId);
                    return presenceRepository.save(record)
                            .then(broadcastPresenceChange(record));
                })
                .then();
    }

    /**
     * Clears the active mission from an actor's presence record,
     * transitioning them to ONLINE_AVAILABLE.
     *
     * @param userId   the actor's user identifier
     * @param tenantId the tenant context
     * @return Mono completing after persistence and broadcast
     */
    public Mono<Void> clearMission(String userId, String tenantId) {
        return presenceRepository.findByUserAndTenant(userId, tenantId)
                .flatMap(record -> {
                    record.clearMission();
                    return presenceRepository.save(record)
                            .then(broadcastPresenceChange(record));
                })
                .then();
    }

    /**
     * Returns the current presence record for an actor.
     *
     * @param userId   the actor's user identifier
     * @param tenantId the tenant context
     * @return Mono with the presence record, or empty if actor is offline
     */
    public Mono<PresenceRecord> getPresence(String userId, String tenantId) {
        return presenceRepository.findByUserAndTenant(userId, tenantId);
    }

    /**
     * Returns all currently online actors within a tenant.
     *
     * @param tenantId the tenant context
     * @return Flux of online presence records
     */
    public Flux<PresenceRecord> getOnlineActors(String tenantId) {
        return presenceRepository.findAllByTenant(tenantId)
                .filter(PresenceRecord::isOnline);
    }

    /**
     * Returns whether a specific actor is currently online.
     *
     * @param userId   the actor's user identifier
     * @param tenantId the tenant context
     * @return Mono<Boolean> true if the actor is online
     */
    public Mono<Boolean> isOnline(String userId, String tenantId) {
        return presenceRepository.findByUserAndTenant(userId, tenantId)
                .map(PresenceRecord::isOnline)
                .defaultIfEmpty(false);
    }

    /**
     * Sweeps for stale presence records (those not updated within the stale duration).
     * Marks them offline and broadcasts the change.
     *
     * @param staleDuration the maximum allowed silence before considering stale
     * @return Mono completing after all stale records are processed
     */
    public Mono<Integer> sweepStalePresences(Duration staleDuration) {
        return presenceRepository.findAllStale(staleDuration)
                .flatMap(record -> {
                    record.markOffline();
                    return presenceRepository.save(record)
                            .then(broadcastPresenceChange(record))
                            .thenReturn(1);
                })
                .reduce(0, Integer::sum)
                .doOnNext(count -> {
                    if (count > 0) {
                        log.info("Swept {} stale presence records to OFFLINE", count);
                    }
                });
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private Mono<Void> broadcastPresenceChange(PresenceRecord record) {
        BroadcastTopic presenceTopic = BroadcastTopic.forPresence(record.getTenantId());
        return broadcaster.broadcast(presenceTopic, record)
                .onErrorResume(ex -> {
                    log.warn("Failed to broadcast presence change for actor {}: {}", record.getUserId(), ex.getMessage());
                    return Mono.empty();
                });
    }
}
