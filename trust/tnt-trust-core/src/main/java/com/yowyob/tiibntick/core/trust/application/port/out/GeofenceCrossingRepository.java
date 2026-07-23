package com.yowyob.tiibntick.core.trust.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.GeofenceCrossingRecord;

/**
 * Outbound Port — {@code GeofenceCrossingRepository}.
 *
 * <p>Persistence contract for {@link GeofenceCrossingRecord} value objects.
 * Implemented by the R2DBC adapter targeting the
 * {@code tnt_trust.geofence_crossings} table in the {@code tnt_trust_db} database.
 *
 * <p>This repository is the local PostgreSQL cache for on-chain geofence
 * crossing data. It is updated asynchronously when
 * {@code yow.trust.events.committed} notifications arrive from {@code yow-trust-event}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface GeofenceCrossingRepository {

    /**
     * Saves a new {@link GeofenceCrossingRecord}.
     *
     * @param crossing the crossing record to persist
     * @return a {@link Mono} emitting the saved record
     */
    Mono<GeofenceCrossingRecord> save(GeofenceCrossingRecord crossing);

    /**
     * Finds the geofence crossing history for an actor, most recent first.
     *
     * @param actorId  the actor's unique identifier
     * @param tenantId the tenant identifier
     * @return a {@link Flux} of crossing records ordered by {@code occurredAt} descending
     */
    Flux<GeofenceCrossingRecord> findByActorId(String actorId, String tenantId);

    /**
     * Updates the Fabric transaction hash for a crossing after on-chain confirmation.
     * Called by {@link com.yowyob.tiibntick.core.trust.adapter.in.kafka.TrustCommittedEventConsumer}.
     *
     * @param crossingId the crossing identifier
     * @param txHash     the Fabric transaction hash
     * @return a {@link Mono} completing when the update is persisted
     */
    Mono<Void> updateTxHash(String crossingId, String txHash);
}
