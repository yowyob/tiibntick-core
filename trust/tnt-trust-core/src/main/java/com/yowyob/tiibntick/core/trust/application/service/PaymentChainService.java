package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordPaymentUseCase;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustProofQueryPort;

/**
 * Application Service — {@code PaymentChainService}.
 *
 * <p>Anchors committed wallet payments on Hyperledger Fabric. Provides an
 * immutable, auditable record of the financial transaction — critical for
 * transparency and dispute resolution in the TiiBnTick marketplace.
 *
 * <p>Implements {@link RecordPaymentUseCase}.
 *
 * <h3>Integration</h3>
 * <p>Called by {@code tnt-billing-wallet} when a MoMo/Stripe webhook confirms
 * successful payment (see {@code WalletService.handleSuccessfulPayment}).
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Service
public class PaymentChainService implements RecordPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentChainService.class);

    private final LogisticEventPublisherService publisherService;
    private final TrustProofQueryPort trustProofQueryPort;
    private final MeterRegistry meterRegistry;

    public PaymentChainService(
            final LogisticEventPublisherService publisherService,
            final TrustProofQueryPort trustProofQueryPort,
            final MeterRegistry meterRegistry) {
        this.publisherService = publisherService;
        this.trustProofQueryPort = trustProofQueryPort;
        this.meterRegistry = meterRegistry;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds a {@code PAYMENT_COMMITTED} {@link LogisticTrustEvent} and
     * publishes it to the Kafka trust topic for Fabric anchoring.
     */
    @Override
    public Mono<String> record(
            final String paymentIntentId,
            final String walletId,
            final String actorId,
            final String tenantId,
            final String channel,
            final String externalRef,
            final String amount,
            final String currency) {

        log.info("Anchoring payment commit — paymentIntentId={}, walletId={}, tenant={}",
                paymentIntentId, walletId, tenantId);

        final LogisticTrustEvent event = LogisticTrustEvent.forPaymentCommitted(
                paymentIntentId, walletId, actorId, tenantId, channel, externalRef, amount, currency);

        return publisherService.publish(event)
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.payment.anchored",
                            "tenant", tenantId).increment();
                    log.info("Payment event published — correlationId={}, paymentIntentId={}",
                            event.getCorrelationId(), paymentIntentId);
                })
                .doOnError(e -> log.error("Failed to anchor payment paymentIntentId={}: {}",
                        paymentIntentId, e.getMessage()))
                .thenReturn(event.getCorrelationId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checks the Trust Event REST API to determine whether the payment
     * has a committed on-chain proof.
     */
    @Override
    public Mono<Boolean> isRecordedOnChain(final String paymentIntentId, final String tenantId) {
        return trustProofQueryPort
                .findTxHashByEntityId(paymentIntentId, "PAYMENT", tenantId)
                .map(txHash -> txHash != null && !txHash.isBlank())
                .defaultIfEmpty(false);
    }
}
