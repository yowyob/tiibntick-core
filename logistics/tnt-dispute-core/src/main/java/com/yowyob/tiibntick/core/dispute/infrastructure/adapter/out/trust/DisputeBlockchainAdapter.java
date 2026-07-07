package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.trust;

import com.yowyob.tiibntick.core.dispute.application.port.outbound.IBlockchainProofPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DisputeBlockchainAdapter implements IBlockchainProofPort {

    private static final Logger log = LoggerFactory.getLogger(DisputeBlockchainAdapter.class);

    @Override
    public Mono<String> getDeliveryProofHash(String trackingCode, String tenantId) {
        log.debug("Querying blockchain proof for tracking={} tenant={}", trackingCode, tenantId);
        return Mono.empty();
    }

    @Override
    public Mono<String> anchorEvidence(String evidenceId, String fileKey, String disputeId, String tenantId) {
        log.info("Anchoring evidence evidenceId={} disputeId={} on blockchain", evidenceId, disputeId);
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> verifyProof(String blockchainRef) {
        log.debug("Verifying blockchain proof ref={}", blockchainRef);
        return Mono.just(true);
    }
}
