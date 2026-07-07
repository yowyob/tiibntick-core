package com.yowyob.tiibntick.core.realtime.application.port.out;

import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import reactor.core.publisher.Mono;

/**
 * Outbound port for pushing real-time GPS coordinates to tnt-actor-core.
 * Updates the actor's last known location stored in actor profile data.
 *
 * @author MANFOUO Braun
 */
public interface IActorLocationUpdater {

    /**
     * Updates the GPS coordinates for a specific actor in tnt-actor-core.
     *
     * @param actorId     the actor's identifier
     * @param tenantId    the tenant context
     * @param coordinates the new GPS coordinates
     * @return Mono completing when the location is updated
     */
    Mono<Void> updateLocation(String actorId, String tenantId, GeoCoordinates coordinates);
}
