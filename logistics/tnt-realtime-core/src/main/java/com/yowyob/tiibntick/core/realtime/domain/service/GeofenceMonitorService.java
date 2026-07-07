package com.yowyob.tiibntick.core.realtime.domain.service;

import com.yowyob.tiibntick.core.realtime.application.port.out.IGeofenceZoneRepository;
import com.yowyob.tiibntick.core.realtime.application.port.out.IRealtimeEventPublisher;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.event.GeofenceTriggerEvent;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import com.yowyob.tiibntick.core.realtime.domain.model.GeofenceTrigger;
import com.yowyob.tiibntick.core.realtime.domain.model.GeofenceZone;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.GeofenceDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain service monitoring deliverers' GPS positions against geofence zones.
 *
 * <p>For each incoming GPS ping, all active zones for the tenant are checked.
 * When a deliverer crosses a zone boundary (enters or exits), a
 * {@link GeofenceTrigger} is emitted, published to Kafka, and broadcast
 * via WebSocket to the tenant's geofence topic.</p>
 *
 * <p>State tracking: this service maintains per-deliverer zone-presence state
 * in memory to detect ENTER/EXIT transitions correctly. This state is
 * per-instance; in a multi-instance deployment, zone crossings detected
 * on different instances may rarely generate duplicate events, which
 * tnt-delivery-core handles idempotently.</p>
 *
 * @author MANFOUO Braun
 */
public class GeofenceMonitorService {

    private static final Logger log = LoggerFactory.getLogger(GeofenceMonitorService.class);

    /** delivererId → set of zoneIds where the deliverer is currently inside */
    private final Map<String, Set<String>> activeZoneMembership = new ConcurrentHashMap<>();

    private final IGeofenceZoneRepository zoneRepository;
    private final IWebSocketBroadcaster broadcaster;
    private final IRealtimeEventPublisher eventPublisher;

    public GeofenceMonitorService(IGeofenceZoneRepository zoneRepository,
                                  IWebSocketBroadcaster broadcaster,
                                  IRealtimeEventPublisher eventPublisher) {
        this.zoneRepository = zoneRepository;
        this.broadcaster = broadcaster;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Checks a GPS ping against all active geofence zones for the tenant.
     * Emits triggers for zone crossings (ENTER or EXIT).
     *
     * @param entry the GPS ping to check
     * @return Mono completing after all zone checks and trigger emissions
     */
    public Mono<Void> checkGeofences(GPSStreamEntry entry) {
        return zoneRepository.findActiveByTenant(entry.tenantId())
                .flatMap(zone -> checkZoneCrossing(entry, zone))
                .then();
    }

    /**
     * Registers a new geofence zone for monitoring.
     *
     * @param zone the zone to register
     * @return Mono completing after persistence
     */
    public Mono<Void> registerZone(GeofenceZone zone) {
        return zoneRepository.save(zone).then();
    }

    /**
     * Removes a zone from monitoring.
     *
     * @param zoneId   the zone identifier
     * @param tenantId the tenant context
     * @return Mono completing after removal
     */
    public Mono<Void> removeZone(String zoneId, String tenantId) {
        return zoneRepository.deleteByIdAndTenant(zoneId, tenantId);
    }

    /**
     * Clears the in-memory zone membership state for a deliverer.
     * Should be called when a deliverer ends a mission or disconnects.
     *
     * @param delivererId the deliverer whose state to clear
     */
    public void clearDelivererState(String delivererId) {
        activeZoneMembership.remove(delivererId);
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private Mono<Void> checkZoneCrossing(GPSStreamEntry entry, GeofenceZone zone) {
        boolean isCurrentlyInside = zone.contains(entry.coordinates());
        Set<String> memberZones = activeZoneMembership
                .computeIfAbsent(entry.delivererId(), k -> ConcurrentHashMap.newKeySet());
        boolean wasPreviouslyInside = memberZones.contains(zone.getId());

        if (isCurrentlyInside && !wasPreviouslyInside) {
            // ENTER transition
            memberZones.add(zone.getId());
            GeofenceTrigger trigger = GeofenceTrigger.of(
                    entry.delivererId(), entry.tenantId(),
                    zone, GeofenceDirection.ENTER,
                    entry.coordinates(), entry.missionId());
            return handleTrigger(trigger);
        } else if (!isCurrentlyInside && wasPreviouslyInside) {
            // EXIT transition
            memberZones.remove(zone.getId());
            GeofenceTrigger trigger = GeofenceTrigger.of(
                    entry.delivererId(), entry.tenantId(),
                    zone, GeofenceDirection.EXIT,
                    entry.coordinates(), entry.missionId());
            return handleTrigger(trigger);
        }

        return Mono.empty();
    }

    private Mono<Void> handleTrigger(GeofenceTrigger trigger) {
        log.info("Geofence {} — actor {} {} zone {} ({})",
                trigger.direction(), trigger.actorId(),
                trigger.isEntry() ? "ENTERED" : "EXITED",
                trigger.zoneName(), trigger.zoneType());

        BroadcastTopic geofenceTopic = BroadcastTopic.forGeofence(trigger.tenantId());
        GeofenceTriggerEvent event = new GeofenceTriggerEvent(trigger.tenantId(), trigger);

        return Mono.when(
                broadcaster.broadcast(geofenceTopic, trigger)
                           .onErrorResume(ex -> {
                               log.warn("Failed to broadcast geofence trigger: {}", ex.getMessage());
                               return Mono.empty();
                           }),
                eventPublisher.publish(event)
                              .onErrorResume(ex -> {
                                  log.warn("Failed to publish geofence event to Kafka: {}", ex.getMessage());
                                  return Mono.empty();
                              })
        );
    }
}
