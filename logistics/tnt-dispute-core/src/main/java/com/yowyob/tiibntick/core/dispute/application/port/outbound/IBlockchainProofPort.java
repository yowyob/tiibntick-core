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
     * @return the blockchain transaction hash
     */
    Mono<String> anchorEvidence(String evidenceId, String fileKey, String disputeId, String tenantId);

    /**
     * Verifies the integrity of a previously anchored evidence record.
     *
     * @param blockchainRef the blockchain transaction hash to verify
     * @return {@code true} if the hash is valid and the record is intact
     */
    Mono<Boolean> verifyProof(String blockchainRef);
}
