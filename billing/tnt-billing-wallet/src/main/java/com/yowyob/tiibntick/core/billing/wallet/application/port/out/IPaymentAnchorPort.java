package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port for anchoring a committed wallet payment on the blockchain.
 *
 * <p>Implemented by an adapter living in {@code tnt-trust-core} (which depends on
 * {@code tnt-billing-wallet} to see this port — never the other way round, to keep
 * the module graph acyclic). Best-effort: a failure here must never fail the
 * payment-confirmation flow, so callers should contain errors before/around this call.
 *
 * @author MANFOUO Braun
 */
public interface IPaymentAnchorPort {

    /** Anchors a committed payment. See {@link PaymentAnchorPayload}. */
    Mono<Void> anchor(PaymentAnchorPayload payload);
}
