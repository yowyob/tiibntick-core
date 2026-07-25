package com.yowyob.tiibntick.core.linkback.application.port.out;

import com.yowyob.tiibntick.core.linkback.domain.model.NetworkAlert;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import reactor.core.publisher.Mono;

/**
 * Outbound port for the Link live-map geohash-tile fan-out (Chantier G — Link BFF +
 * temps réel par tuiles geohash, Audit n6 S23 / Audit n7 P1 bis). Implementations
 * publish on the tile channel covering the entity's current location so that only
 * clients whose viewport includes that tile receive the update — never a
 * tenant-wide broadcast.
 *
 * @author MANFOUO Braun
 */
public interface ILinkTileEventPublisher {

    /**
     * Publishes a network node's current position/state on its geohash tile channel.
     * Callers are expected to only invoke this when the node's tile actually changed
     * (or on first fix) — see {@code NetworkNodeApplicationService.updateLocation} —
     * so idle nodes don't generate tile traffic.
     */
    Mono<Void> publishNodeMoved(NetworkNode node);

    /** Publishes a newly reported alert on its geohash tile channel. */
    Mono<Void> publishAlertReported(NetworkAlert alert);

    /** Publishes an alert's resolution on its geohash tile channel (the hazard leaves the map). */
    Mono<Void> publishAlertResolved(NetworkAlert alert);
}
