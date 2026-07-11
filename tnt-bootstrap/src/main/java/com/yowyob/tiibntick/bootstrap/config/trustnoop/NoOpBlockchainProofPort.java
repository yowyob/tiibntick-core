package com.yowyob.tiibntick.bootstrap.config.trustnoop;

import com.yowyob.tiibntick.core.dispute.application.port.outbound.IBlockchainProofPort;
import reactor.core.publisher.Mono;

/**
 * No-op fallback for {@link IBlockchainProofPort}, wired only when
 * {@code tnt.trust.enabled=false} — see {@code TrustNoOpFallbackConfig}.
 *
 * @author MANFOUO Braun
 */
public class NoOpBlockchainProofPort implements IBlockchainProofPort {

    @Override
    public Mono<String> getDeliveryProofHash(final String trackingCode, final String tenantId) {
        return Mono.empty();
    }

    @Override
    public Mono<String> anchorEvidence(
            final String evidenceId, final String fileKey, final String disputeId,
            final String tenantId, final String evidenceHash) {
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> verifyProof(final String blockchainRef, final String expectedHash) {
        return Mono.just(false);
    }
}
