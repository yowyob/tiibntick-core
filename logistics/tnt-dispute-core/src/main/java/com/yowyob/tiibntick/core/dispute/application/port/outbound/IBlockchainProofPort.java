package com.yowyob.tiibntick.core.dispute.application.port.outbound;

import reactor.core.publisher.Mono;

/**
 * Secondary port for querying tnt-trust (blockchain layer) for delivery proofs
 * and anchoring dispute evidence on the blockchain.
 *
 * @author MANFOUO Braun
 */
public interface IBlockchainProofPort {

    /**
     * Retrieves the blockchain delivery proof hash for a given package tracking code.
     * Used during investigation to verify delivery claims.
     *
     * @param trackingCode the package tracking code
     * @param tenantId     the tenant scope
     * @return the blockchain transaction hash, or empty if not found
     */
    Mono<String> getDeliveryProofHash(String trackingCode, String tenantId);

    /**
     * Anchors a piece of evidence on the blockchain via tnt-trust.
     * Returns the blockchain transaction reference.
     *
     * @param evidenceId   the evidence ID to anchor
     * @param fileKey      the MinIO object key of the evidence file
     * @param disputeId    the dispute ID for context
     * @param tenantId     the tenant scope
     * @param evidenceHash SHA-256 hash of the evidence content, nullable — enables real
     *                     cryptographic verification later via {@link #verifyProof}
     * @return the blockchain transaction hash
     */
    Mono<String> anchorEvidence(String evidenceId, String fileKey, String disputeId, String tenantId,
                                 String evidenceHash);

    /**
     * Verifies the integrity of a previously anchored evidence record by comparing
     * an independently recomputed data hash against what was anchored on-chain.
     *
     * @param blockchainRef the blockchain transaction hash to verify
     * @param expectedHash  the SHA-256 evidence hash expected to match the anchored record
     * @return {@code true} if the hash matches and the record is intact
     */
    Mono<Boolean> verifyProof(String blockchainRef, String expectedHash);
}
