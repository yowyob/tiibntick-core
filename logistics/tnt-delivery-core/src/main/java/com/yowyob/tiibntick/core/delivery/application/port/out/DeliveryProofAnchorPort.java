package com.yowyob.tiibntick.core.delivery.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port for anchoring a completed delivery's proof on the blockchain.
 *
 * <p>Implemented by an adapter living in {@code tnt-trust-core} (which depends on
 * {@code tnt-delivery-core} to see this port — never the other way round, to keep
 * the module graph acyclic). Best-effort: a failure here must never fail the
 * delivery-completion flow, so callers should contain errors before/around this call.
 *
 * @author MANFOUO Braun
 */
public interface DeliveryProofAnchorPort {

    /**
     * Anchors a delivery proof. Only called when real proof data (photo hash) is available —
     * see {@link DeliveryProofAnchorPayload}.
     */
    Mono<Void> anchor(DeliveryProofAnchorPayload payload);
}
