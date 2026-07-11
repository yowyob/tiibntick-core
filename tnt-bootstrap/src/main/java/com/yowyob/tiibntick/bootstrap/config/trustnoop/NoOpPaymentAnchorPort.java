package com.yowyob.tiibntick.bootstrap.config.trustnoop;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentAnchorPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.PaymentAnchorPayload;
import reactor.core.publisher.Mono;

/**
 * No-op fallback for {@link IPaymentAnchorPort}, wired only when
 * {@code tnt.trust.enabled=false} — see {@code TrustNoOpFallbackConfig}.
 *
 * @author MANFOUO Braun
 */
public class NoOpPaymentAnchorPort implements IPaymentAnchorPort {

    @Override
    public Mono<Void> anchor(final PaymentAnchorPayload payload) {
        return Mono.empty();
    }
}
