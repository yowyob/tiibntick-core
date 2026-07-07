package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.MoMoPayload;
import reactor.core.publisher.Mono;

/**
 * Secondary port — abstraction for Mobile Money and Stripe payment providers.
 * Implementations: MtnMoMoAdapter, OrangeMoneyAdapter, StripeAdapter.
 * @author MANFOUO Braun
 */
public interface IMoMoPaymentPort {

    /**
     * Initiates an MTN MoMo Collections request (USSD push).
     *
     * @param payload   the request body built from the domain
     * @param referenceId unique X-Reference-Id UUID for MTN API
     * @return the MTN referenceId returned in the response (same as referenceId)
     */
    Mono<String> initiateMtnCollection(MoMoPayload payload, String referenceId);

    /**
     * Initiates an Orange Money payment request.
     *
     * @param payload the request body built from the domain
     * @return the Orange payment URL for redirect
     */
    Mono<String> initiateOrangePayment(MoMoPayload payload);

    /**
     * Checks the current status of an MTN MoMo payment.
     *
     * @param referenceId the X-Reference-Id sent during initiation
     * @return provider status string (SUCCESSFUL, FAILED, PENDING)
     */
    Mono<String> checkMtnPaymentStatus(String referenceId);

    /**
     * Initiates a Stripe payment intent.
     *
     * @param payload payment details (amount, currency, description)
     * @param idempotencyKey Stripe idempotency key
     * @return Stripe PaymentIntent client_secret
     */
    Mono<String> initiateStripePayment(MoMoPayload payload, String idempotencyKey);

    /**
     * Initiates a Stripe refund for a confirmed payment.
     *
     * @param stripePaymentIntentId Stripe's paymentIntentId to refund
     * @return Stripe refund ID
     */
    Mono<String> refundStripePayment(String stripePaymentIntentId);
}
