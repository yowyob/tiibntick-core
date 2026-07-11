package com.yowyob.tiibntick.core.billing.pricing.domain.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port for anchoring a billing policy activation on the blockchain.
 *
 * <p>Implemented by an adapter living in {@code tnt-trust-core} (which depends on
 * {@code tnt-billing-pricing} to see this port — never the other way round, to keep
 * the module graph acyclic). Best-effort: a failure here must never fail the
 * policy-activation flow, so callers should contain errors before/around this call.
 *
 * @author MANFOUO Braun
 */
public interface BillingPolicyAnchorPort {

    /** Anchors a billing policy activation. See {@link BillingPolicyAnchorPayload}. */
    Mono<Void> anchor(BillingPolicyAnchorPayload payload);
}
