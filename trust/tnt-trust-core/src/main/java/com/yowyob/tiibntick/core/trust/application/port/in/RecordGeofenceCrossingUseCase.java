package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Mono;

/**
 * Inbound Port — {@code RecordGeofenceCrossingUseCase}.
 *
 * <p>Anchors a geofence zone crossing (entry or exit) on Hyperledger Fabric via
 * {@code yow-trust-event}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface RecordGeofenceCrossingUseCase {

    /**
     * Records a geofence crossing on the blockchain.
     *
     * @param actorId   the deliverer who crossed the zone boundary
     * @param tenantId  the tenant identifier
     * @param zoneId    the geofence zone identifier
     * @param zoneName  the geofence zone's display name
     * @param zoneType  the zone classification (e.g. RELAY_HUB, DANGER_ZONE)
     * @param direction {@code ENTER} or {@code EXIT}
     * @param gpsLat    latitude at crossing time
     * @param gpsLng    longitude at crossing time
     * @param missionId the mission in progress at crossing time, if any
     * @return a {@link Mono} emitting the correlation id used to track the anchoring
     */
    Mono<String> record(String actorId, String tenantId, String zoneId, String zoneName,
                         String zoneType, String direction, double gpsLat, double gpsLng,
                         String missionId);
}
