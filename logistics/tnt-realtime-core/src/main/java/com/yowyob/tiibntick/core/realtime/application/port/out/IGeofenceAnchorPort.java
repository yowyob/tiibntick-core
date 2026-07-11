package com.yowyob.tiibntick.core.realtime.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port for anchoring a geofence zone crossing on the blockchain.
 *
 * <p>Implemented by an adapter living in {@code tnt-trust-core} (which depends on
 * {@code tnt-realtime-core} to see this port — never the other way round, to keep
 * the module graph acyclic). Best-effort: a failure here must never fail the
 * geofence-monitoring flow, so callers should contain errors before/around this call.
 *
 * @author MANFOUO Braun
 */
public interface IGeofenceAnchorPort {

    /**
     * Anchors a geofence crossing (zone entry or exit).
     *
     * @param payload the crossing data to anchor
     */
    Mono<Void> anchor(GeofenceAnchorPayload payload);
}
