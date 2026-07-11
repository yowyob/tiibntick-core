package com.yowyob.tiibntick.core.trust.adapter.out.dispute;

import com.yowyob.tiibntick.core.dispute.application.port.outbound.IBlockchainProofPort;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordDisputeEvidenceUseCase;
import com.yowyob.tiibntick.core.trust.application.port.out.CustodyTransferCacheRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustProofQueryPort;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * tnt-trust-core implementation of {@link IBlockchainProofPort} (outbound port
 * owned by tnt-dispute-core).
 *
 * <p>tnt-trust-core depends on tnt-dispute-core (one-directional, no Maven cycle —
 * dispute never depends back on trust) purely to see this port; it delegates to
 * {@link RecordDisputeEvidenceUseCase} for anchoring, to the local custody-transfer
 * cache for delivery-proof lookups, and to {@link TrustProofQueryPort} for real
 * cryptographic proof verification.
 *
 * <p>Replaces {@code DisputeBlockchainAdapter}, a no-op stub that previously lived
 * inside {@code tnt-dispute-core} itself — that stub was deleted (it would otherwise
 * conflict with this bean, both being unconditional {@code @Component}s implementing
 * the same port).
 *
 * @author MANFOUO Braun
 * @see IBlockchainProofPort
 */
@Component
@RequiredArgsConstructor
public class DisputeBlockchainProofAdapter implements IBlockchainProofPort {

    private static final Logger log = LoggerFactory.getLogger(DisputeBlockchainProofAdapter.class);

    private final RecordDisputeEvidenceUseCase recordDisputeEvidence;
    private final CustodyTransferCacheRepository custodyTransferCacheRepository;
    private final TrustProofQueryPort trustProofQueryPort;

    @Override
    public Mono<String> getDeliveryProofHash(final String trackingCode, final String tenantId) {
        return custodyTransferCacheRepository.findByTrackingCode(trackingCode, tenantId)
                .filter(record -> record.getBlockchainTxHash() != null && !record.getBlockchainTxHash().isBlank())
                .map(CustodyTransferRecord::getBlockchainTxHash)
                .takeLast(1)
                .next();
    }

    @Override
    public Mono<String> anchorEvidence(final String evidenceId, final String fileKey,
                                        final String disputeId, final String tenantId,
                                        final String evidenceHash) {
        return recordDisputeEvidence.record(disputeId, evidenceId, fileKey, tenantId, evidenceHash)
                .onErrorResume(e -> {
                    log.warn("Failed to anchor dispute evidence on-chain — evidenceId={}, disputeId={}: {}",
                            evidenceId, disputeId, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link TrustProofQueryPort#verifyProof} for a real cryptographic
     * comparison when an {@code expectedHash} is supplied (evidence submitted after the
     * evidence-hash field was introduced). Falls back to a structural presence check for
     * older evidence records anchored before {@code evidenceHash} existed.
     */
    @Override
    public Mono<Boolean> verifyProof(final String blockchainRef, final String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return Mono.just(blockchainRef != null && !blockchainRef.isBlank());
        }
        return trustProofQueryPort.verifyProof(blockchainRef, expectedHash);
    }
}
