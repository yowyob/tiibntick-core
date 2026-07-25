package com.yowyob.tiibntick.core.linkback.application.port.out;

import com.yowyob.tiibntick.core.linkback.domain.model.LinkPosition;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for the Link "latest known position" read model (Chantier G, Audit n5 P-17 /
 * Audit n6 S26) — replaces the synchronous {@code DeliveryQueryUseCase} pull with a cache fed
 * by {@code tnt.realtime.gps.position.updated}.
 *
 * @author MANFOUO Braun
 */
public interface ILinkPositionCache {

    /**
     * Stores the position, but only if it's newer than whatever is already cached for this
     * mission — tolerates Kafka delivering positions out of order (see
     * {@code GpsPositionKafkaConsumer} javadoc) without depending on partition-key ordering.
     */
    Mono<Void> saveIfNewer(UUID tenantId, LinkPosition position);

    Mono<LinkPosition> findLatest(UUID tenantId, String missionId);
}
