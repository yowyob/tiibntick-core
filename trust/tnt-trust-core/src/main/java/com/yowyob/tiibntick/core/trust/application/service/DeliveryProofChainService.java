package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.application.port.in.GetDeliveryAuditTrailUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordCustodyTransferUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordDeliveryProofUseCase;
import com.yowyob.tiibntick.core.trust.application.port.out.CustodyTransferCacheRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.DeliveryProofCacheRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustProofQueryPort;

/**
 * Application Service — {@code DeliveryProofChainService}.
 *
 * <p>Manages the "Fil d'Ariane" — TiiBnTick's immutable blockchain audit trail
 * for delivery missions and package custody chains.
 *
 * <p>Implements:
 * <ul>
 *   <li>{@link RecordDeliveryProofUseCase} — anchors a delivery proof on Fabric</li>
 *   <li>{@link RecordCustodyTransferUseCase} — anchors a custody transfer on Fabric</li>
 *   <li>{@link GetDeliveryAuditTrailUseCase} — queries the audit trail</li>
 * </ul>
 *
 * <h3>Recording Flow</h3>
 * <pre>
 *   DeliveryProofRecord / CustodyTransferRecord
 *     → Create LogisticTrustEvent
 *     → Persist to local cache (DeliveryProofCacheRepository / CustodyTransferCacheRepository)
 *     → Publish to Kafka via LogisticEventPublisherService
 *     → Return correlation ID (txHash arrives asynchronously via committed event)
 * </pre>
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Service
public class DeliveryProofChainService
        implements RecordDeliveryProofUseCase, RecordCustodyTransferUseCase, GetDeliveryAuditTrailUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeliveryProofChainService.class);

    private final LogisticEventPublisherService publisherService;
    private final DeliveryProofCacheRepository proofCacheRepository;
    private final CustodyTransferCacheRepository custodyCacheRepository;
    private final TrustProofQueryPort trustProofQueryPort;
    private final MeterRegistry meterRegistry;

    public DeliveryProofChainService(
            final LogisticEventPublisherService publisherService,
            final DeliveryProofCacheRepository proofCacheRepository,
            final CustodyTransferCacheRepository custodyCacheRepository,
            final TrustProofQueryPort trustProofQueryPort,
            final MeterRegistry meterRegistry) {
        this.publisherService = publisherService;
        this.proofCacheRepository = proofCacheRepository;
        this.custodyCacheRepository = custodyCacheRepository;
        this.trustProofQueryPort = trustProofQueryPort;
        this.meterRegistry = meterRegistry;
    }

    // ── RecordDeliveryProofUseCase ────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Persists the proof to the local cache and publishes the corresponding
     * {@link LogisticTrustEvent} to Kafka. The Fabric tx hash will be updated
     * asynchronously when {@code yow-trust-event} confirms the commit.
     * Returns the {@code correlationId} as an immediate tracking reference.
     */
    @Override
    @Transactional
    public Mono<String> record(final DeliveryProofRecord proof) {
        log.info("Recording delivery proof on blockchain — proofId={}, missionId={}, actor={}",
                proof.getProofId(), proof.getMissionId(), proof.getActorId());

        final LogisticTrustEvent event = LogisticTrustEvent.forDeliveryProof(
                proof, proof.getMissionId(), proof.getActorId());

        return proofCacheRepository.save(proof)
                .flatMap(saved -> publisherService.publish(event))
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.delivery.proof.recorded",
                            "tenant", proof.getTenantId()).increment();
                    log.info("Delivery proof published to Fabric pipeline — correlationId={}",
                            event.getCorrelationId());
                })
                .thenReturn(event.getCorrelationId());
    }

    // ── RecordCustodyTransferUseCase ──────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Persists the transfer to the local cache and publishes it to Kafka.
     * The Fabric tx hash is updated asynchronously via the committed Kafka topic.
     */
    @Override
    @Transactional
    public Mono<String> record(final CustodyTransferRecord transfer) {
        log.info("Recording custody transfer — transferId={}, package={}, {}→{}",
                transfer.getTransferId(), transfer.getTrackingCode(),
                transfer.getFromActorId(), transfer.getToActorId());

        final LogisticTrustEvent event = LogisticTrustEvent.forCustodyTransfer(transfer);

        return custodyCacheRepository.save(transfer)
                .flatMap(saved -> publisherService.publish(event))
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.custody.transfer.recorded",
                            "tenant", transfer.getTenantId()).increment();
                })
                .thenReturn(event.getCorrelationId());
    }

    // ── GetDeliveryAuditTrailUseCase ──────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    public Flux<DeliveryProofRecord> getByMissionId(final String missionId, final String tenantId) {
        log.debug("Fetching delivery audit trail for missionId={}", missionId);
        return proofCacheRepository.findByMissionId(missionId, tenantId);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<CustodyTransferRecord> getByPackageTrackingCode(
            final String trackingCode, final String tenantId) {
        log.debug("Fetching chain of custody for trackingCode={}", trackingCode);
        return custodyCacheRepository.findByTrackingCode(trackingCode, tenantId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the Trust Proof Query Port which calls the
     * Trust Event internal REST API at {@code /kernel/trust/events/verify}.
     */
    @Override
    public Mono<Boolean> verifyDeliveryProof(final String txHash, final String expectedHash) {
        log.info("Verifying delivery proof on-chain — txHash={}", txHash);
        return trustProofQueryPort.verifyProof(txHash, expectedHash);
    }
}
