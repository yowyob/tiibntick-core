package com.yowyob.tiibntick.bootstrap.config.trustnoop;

import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.BillingPolicyAnchorPayload;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.BillingPolicyAnchorPort;
import reactor.core.publisher.Mono;

/**
 * No-op fallback for {@link BillingPolicyAnchorPort}, wired only when
 * {@code tnt.trust.enabled=false} — see {@code TrustNoOpFallbackConfig}.
 *
 * @author MANFOUO Braun
 */
public class NoOpBillingPolicyAnchorPort implements BillingPolicyAnchorPort {

    @Override
    public Mono<Void> anchor(final BillingPolicyAnchorPayload payload) {
        return Mono.empty();
    }
}
