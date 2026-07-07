package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntentId;
import reactor.core.publisher.Mono;

/**
 * Secondary port — persistence contract for PaymentIntent entities.
 * @author MANFOUO Braun
 */
public interface IPaymentIntentRepository {
    Mono<PaymentIntent> save(PaymentIntent intent);
    Mono<PaymentIntent> findById(PaymentIntentId id);
    Mono<PaymentIntent> findByExternalRef(String externalRef);
    Mono<PaymentIntent> findByIdempotencyKey(String idempotencyKey);
    Mono<PaymentIntent> findByInvoiceId(String invoiceId);
}
