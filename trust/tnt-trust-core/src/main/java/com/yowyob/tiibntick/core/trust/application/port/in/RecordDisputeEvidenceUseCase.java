package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Mono;

/**
 * Inbound Port — {@code RecordDisputeEvidenceUseCase}.
 *
 * <p>Anchors a piece of dispute evidence on Hyperledger Fabric for tamper-proof
 * mediation. Called by {@code tnt-dispute-core} when evidence of a type worth
 * anchoring (blockchain proof, delivery proof record, GPS trace) is submitted.
 *
 * <p>Implemented by {@link com.yowyob.tiibntick.core.trust.application.service.DisputeEvidenceChainService}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface RecordDisputeEvidenceUseCase {

    /**
     * Anchors a piece of dispute evidence on the blockchain.
     *
     * @param disputeId    the dispute identifier
     * @param evidenceId   the evidence identifier
     * @param fileKey      the MinIO object key of the evidence file
     * @param tenantId     the tenant identifier
     * @param evidenceHash SHA-256 hash of the evidence content, nullable
     * @return a {@link Mono} emitting the Fabric transaction hash
     */
    Mono<String> record(String disputeId, String evidenceId, String fileKey, String tenantId,
                         String evidenceHash);
}
