package com.yowyob.tiibntick.bootstrap.config.trustnoop;

import com.yowyob.tiibntick.core.trust.application.port.in.RecordPaymentUseCase;
import reactor.core.publisher.Mono;

/**
 * No-op fallback for {@link RecordPaymentUseCase}, wired only when
 * {@code tnt.trust.enabled=false} — see {@code TrustNoOpFallbackConfig}.
 *
 * @author MANFOUO Braun
 */
public class NoOpRecordPaymentUseCase implements RecordPaymentUseCase {

    @Override
    public Mono<String> record(final String paymentIntentId, final String walletId,
                               final String actorId, final String tenantId,
                               final String channel, final String externalRef,
                               final String amount, final String currency) {
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> isRecordedOnChain(final String paymentIntentId, final String tenantId) {
        return Mono.just(false);
    }
}
