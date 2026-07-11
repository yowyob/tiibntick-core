package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Mono;

/**
 * Inbound Port — {@code RecordPaymentUseCase}.
 *
 * <p>Anchors a committed wallet payment on Hyperledger Fabric.
 * Called by {@code tnt-billing-wallet} when a MoMo/Stripe webhook confirms
 * successful payment. The on-chain record serves as immutable proof of the
 * transaction for audit and dispute-resolution purposes.
 *
 * <p>Implemented by {@link com.yowyob.tiibntick.core.trust.application.service.PaymentChainService}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface RecordPaymentUseCase {

    /**
     * Anchors a committed payment on the blockchain.
     *
     * @param paymentIntentId the payment intent identifier
     * @param walletId        the wallet identifier that was debited/credited
     * @param actorId         the wallet owner's actor id
     * @param tenantId        the tenant identifier
     * @param channel         the payment channel (e.g., MTN_MOMO, ORANGE_MONEY, STRIPE)
     * @param externalRef     the provider's financial transaction id
     * @param amount          the committed amount (plain string, e.g. "1000.00")
     * @param currency        the ISO currency code (e.g., "XAF")
     * @return a {@link Mono} emitting the Fabric transaction hash (as correlation ID)
     */
    Mono<String> record(String paymentIntentId, String walletId, String actorId,
                        String tenantId, String channel, String externalRef,
                        String amount, String currency);

    /**
     * Retrieves the on-chain record for a payment to confirm it was
     * recorded on the Hyperledger Fabric ledger.
     *
     * @param paymentIntentId the payment intent identifier
     * @param tenantId        the tenant identifier
     * @return a {@link Mono} emitting {@code true} if confirmed on-chain
     */
    Mono<Boolean> isRecordedOnChain(String paymentIntentId, String tenantId);
}
