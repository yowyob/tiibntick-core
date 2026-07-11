package com.yowyob.tiibntick.core.trust.application.port.out;

import com.yowyob.tiibntick.core.trust.domain.model.valueobject.BlockchainProof;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound Port — {@code TrustProofQueryPort}.
 *
 * <p>Defines the contract for querying blockchain proof data from the
 * {@code yow-trust-event} Kernel microservice via its internal REST API.
 *
 * <p>Implemented by {@link com.yowyob.tiibntick.core.trust.adapter.out.rest.TrustEventRestClientAdapter},
 * which uses a reactive WebClient targeting the Trust Event internal API at
 * {@code /kernel/trust/events}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface TrustProofQueryPort {

    /**
     * Finds the blockchain proof for a given domain entity by querying
     * the Trust Event microservice.
     *
     * @param entityId   the domain entity identifier
     * @param entityType the domain entity type (e.g., "DELIVERY_PROOF")
     * @param tenantId   the tenant identifier
     * @return a {@link Mono} emitting the tx hash if found, or empty
     */
    Mono<String> findTxHashByEntityId(String entityId, String entityType, String tenantId);

    /**
     * Verifies a blockchain proof on-chain via the Trust Event microservice.
     *
     * @param txHash       the Fabric transaction hash
     * @param expectedHash the expected SHA-256 data hash
     * @return a {@link Mono} emitting {@code true} if the proof is valid
     */
    Mono<Boolean> verifyProof(String txHash, String expectedHash);

    /**
     * Retrieves the complete audit history for a domain entity
     * as a sequence of Fabric transaction hashes (ordered chronologically).
     *
     * @param entityId   the domain entity identifier
     * @param entityType the domain entity type
     * @return a {@link Flux} of tx hashes, oldest first
     */
    Flux<String> getAuditHistory(String entityId, String entityType);

    /**
     * Retrieves the complete audit history for a domain entity as rich
     * {@link BlockchainProof} objects including payload, proof hashes and event type.
     *
     * @param entityId   the domain entity identifier
     * @param entityType the domain entity type (e.g., "CUSTODY_TRANSFER")
     * @param tenantId   the tenant identifier
     * @return a {@link Flux} of {@link BlockchainProof}, oldest first
     */
    Flux<BlockchainProof> getAuditHistoryWithDetails(String entityId, String entityType, String tenantId);
}
