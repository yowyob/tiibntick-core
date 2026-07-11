package com.yowyob.tiibntick.core.actor.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port for anchoring an earned badge on the blockchain.
 *
 * <p>Implemented by an adapter living in {@code tnt-trust-core} (which depends on
 * {@code tnt-actor-core} to see this port — never the other way round; {@code tnt-actor-core}
 * (L2 identity) must never depend on {@code tnt-trust-core} (L3 logistics), which would be a
 * dependency on a higher layer). Best-effort: a failure here must never fail badge earning.
 *
 * @author MANFOUO Braun
 */
public interface IBadgeAnchorPort {

    /**
     * Anchors a badge award and returns the resulting Fabric transaction hash.
     *
     * @param payload the badge data to anchor, see {@link BadgeAnchorPayload}
     * @return a {@link Mono} emitting the tx hash on success, or erroring on failure
     *         (callers are expected to contain the error — anchoring is best-effort)
     */
    Mono<String> anchor(BadgeAnchorPayload payload);
}
