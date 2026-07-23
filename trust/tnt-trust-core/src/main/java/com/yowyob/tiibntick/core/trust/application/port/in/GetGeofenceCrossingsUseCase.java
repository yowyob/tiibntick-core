package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Flux;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.GeofenceCrossingRecord;

/**
 * Inbound Port — {@code GetGeofenceCrossingsUseCase}.
 *
 * <p>Retrieves the geofence zone crossing history for a TiiBnTick actor.
 * Results sourced from the local PostgreSQL cache (tnt_trust schema).
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface GetGeofenceCrossingsUseCase {

    /**
     * Retrieves the geofence crossing history for an actor.
     *
     * @param actorId  the actor's unique identifier
     * @param tenantId the tenant identifier
     * @return a {@link Flux} of crossing records, most recent first
     */
    Flux<GeofenceCrossingRecord> getByActorId(String actorId, String tenantId);
}
