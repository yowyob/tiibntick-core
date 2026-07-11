package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.BillingPolicyRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordBillingPolicyUseCase;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustProofQueryPort;

/**
 * Application Service — {@code BillingPolicyChainService}.
 *
 * <p>Anchors billing policy activations on Hyperledger Fabric.
 * Provides an immutable, auditable record of pricing terms — critical for
 * transparency and dispute resolution in the TiiBnTick marketplace under
 * the OHADA commercial framework applicable in Cameroon.
 *
 * <p>Implements {@link RecordBillingPolicyUseCase}.
 *
 * <h3>Integration</h3>
 * <p>Called by {@code tnt-billing-pricing} when:
 * <ul>
 *   <li>A new {@code BillingPolicy} is activated ({@code status = ACTIVE})</li>
 *   <li>A pricing rule change is committed</li>
 * </ul>
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Service
public class BillingPolicyChainService implements RecordBillingPolicyUseCase {

    private static final Logger log = LoggerFactory.getLogger(BillingPolicyChainService.class);

    private final LogisticEventPublisherService publisherService;
    private final TrustProofQueryPort trustProofQueryPort;
    private final MeterRegistry meterRegistry;

    public BillingPolicyChainService(
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
     * <p>Creates a {@link BillingPolicyRecord}, builds a
     * {@code BILLING_POLICY_ACTIVATED} {@link LogisticTrustEvent}, and
     * publishes it to the Kafka trust topic for Fabric anchoring.
     */
    @Override
    public Mono<String> record(
            final String agencyId,
            final String policyId,
            final String tenantId,
            final String policySummaryJson) {

        log.info("Anchoring billing policy activation — policyId={}, agencyId={}, tenant={}",
                policyId, agencyId, tenantId);

        final BillingPolicyRecord policyRecord = BillingPolicyRecord.activate(
                policyId, agencyId, tenantId, policySummaryJson);

        final LogisticTrustEvent event = LogisticTrustEvent.forBillingPolicyActivated(
                agencyId, policyId, tenantId);

        return publisherService.publish(event)
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.billing.policy.anchored",
                            "tenant", tenantId).increment();
                    log.info("Billing policy event published — correlationId={}, policyId={}",
                            event.getCorrelationId(), policyId);
                })
                .doOnError(e -> log.error("Failed to anchor billing policy policyId={}: {}",
                        policyId, e.getMessage()))
                .thenReturn(event.getCorrelationId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checks the Trust Event REST API to determine whether the billing
     * policy has a committed on-chain proof.
     */
    @Override
    public Mono<Boolean> isRecordedOnChain(final String policyId, final String tenantId) {
        return trustProofQueryPort
                .findTxHashByEntityId(policyId, "BILLING_POLICY", tenantId)
                .map(txHash -> txHash != null && !txHash.isBlank())
                .defaultIfEmpty(false);
    }
}
