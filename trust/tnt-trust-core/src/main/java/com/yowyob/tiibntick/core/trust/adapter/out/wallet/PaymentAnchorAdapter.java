package com.yowyob.tiibntick.core.trust.adapter.out.wallet;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentAnchorPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.PaymentAnchorPayload;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordPaymentUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * tnt-trust-core implementation of {@link IPaymentAnchorPort} (outbound port
 * owned by tnt-billing-wallet).
 *
 * <p>tnt-trust-core depends on tnt-billing-wallet (one-directional, no Maven cycle —
 * billing-wallet never depends back on trust) purely to see this port and its payload
 * type; it delegates to {@link RecordPaymentUseCase}.
 *
 * @author MANFOUO Braun
 * @see IPaymentAnchorPort
 */
@Component
@RequiredArgsConstructor
public class PaymentAnchorAdapter implements IPaymentAnchorPort {

    private final RecordPaymentUseCase recordPayment;

    @Override
    public Mono<Void> anchor(PaymentAnchorPayload payload) {
        return recordPayment.record(
                        payload.paymentIntentId().toString(),
                        payload.walletId().toString(),
                        payload.userId() != null ? payload.userId().toString() : null,
                        payload.tenantId().toString(),
                        payload.channel(),
                        payload.externalRef(),
                        payload.amount().toPlainString(),
                        payload.currency())
                .then();
    }
}
