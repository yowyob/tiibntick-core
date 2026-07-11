package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordDisputeEvidenceUseCase;

/**
 * Application Service — {@code DisputeEvidenceChainService}.
 *
 * <p>Anchors dispute evidence on Hyperledger Fabric. Provides an immutable,
 * auditable record of evidence submitted during dispute mediation.
 *
 * <p>Implements {@link RecordDisputeEvidenceUseCase}.
 *
 * <h3>Integration</h3>
 * <p>Called by {@code tnt-dispute-core} when evidence worth anchoring
 * (blockchain proof, delivery proof record, GPS trace) is submitted.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Service
public class DisputeEvidenceChainService implements RecordDisputeEvidenceUseCase {

    private static final Logger log = LoggerFactory.getLogger(DisputeEvidenceChainService.class);

    private final LogisticEventPublisherService publisherService;
    private final MeterRegistry meterRegistry;

    public DisputeEvidenceChainService(
            final LogisticEventPublisherService publisherService,
            final MeterRegistry meterRegistry) {
        this.publisherService = publisherService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds a {@code DISPUTE_EVIDENCE_ANCHORED} {@link LogisticTrustEvent} and
     * publishes it to the Kafka trust topic for Fabric anchoring.
     */
    @Override
    public Mono<String> record(
            final String disputeId,
            final String evidenceId,
            final String fileKey,
            final String tenantId,
            final String evidenceHash) {

        log.info("Anchoring dispute evidence — disputeId={}, evidenceId={}, tenant={}",
                disputeId, evidenceId, tenantId);

        final LogisticTrustEvent event = LogisticTrustEvent.forDisputeEvidenceAnchored(
                disputeId, evidenceId, fileKey, tenantId, evidenceHash);

        return publisherService.publish(event)
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.dispute.evidence.anchored",
                            "tenant", tenantId).increment();
                    log.info("Dispute evidence event published — correlationId={}, evidenceId={}",
                            event.getCorrelationId(), evidenceId);
                })
                .doOnError(e -> log.error("Failed to anchor dispute evidence evidenceId={}: {}",
                        evidenceId, e.getMessage()))
                .thenReturn(event.getCorrelationId());
    }
}
