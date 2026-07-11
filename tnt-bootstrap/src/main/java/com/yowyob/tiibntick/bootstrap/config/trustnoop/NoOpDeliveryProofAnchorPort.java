package com.yowyob.tiibntick.bootstrap.config.trustnoop;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryProofAnchorPayload;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryProofAnchorPort;
import reactor.core.publisher.Mono;

/**
 * No-op fallback for {@link DeliveryProofAnchorPort}, wired only when
 * {@code tnt.trust.enabled=false} — see {@code TrustNoOpFallbackConfig}.
 *
 * @author MANFOUO Braun
 */
public class NoOpDeliveryProofAnchorPort implements DeliveryProofAnchorPort {

    @Override
    public Mono<Void> anchor(final DeliveryProofAnchorPayload payload) {
        return Mono.empty();
    }
}
