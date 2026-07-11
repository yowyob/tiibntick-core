package com.yowyob.tiibntick.core.trust.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;

/**
 * Outbound Port — {@code DeliveryProofCacheRepository}.
 *
 * <p>Local PostgreSQL cache for {@link DeliveryProofRecord} instances.
 * Records are written after publication to Kafka and updated when
 * the {@code yow.trust.events.committed} confirmation is received.
 *
 * <p>Targets the {@code tnt_trust.delivery_proofs} table.
 *
 * <p><strong>v1.1:</strong> Added {@link #findLatestConfirmedHashByParcelId(String)}
 * to support {@code IBlockchainAuditPort.getParcelChainTailHash()} in
 * {@link com.yowyob.tiibntick.core.trust.adapter.out.incident.IncidentBlockchainAuditAdapter}.
 *
 * @author MANFOUO Braun
 * @version 1.1
 */
public interface DeliveryProofCacheRepository {

    /**
     * Saves or updates a delivery proof record.
     *
     * @param proof the delivery proof to persist
     * @return a {@link Mono} emitting the saved proof
     */
    Mono<DeliveryProofRecord> save(DeliveryProofRecord proof);

    /**
     * Finds a delivery proof by its unique identifier.
     *
     * @param proofId the proof identifier
     * @return a {@link Mono} emitting the proof, or empty if not found
     */
    Mono<DeliveryProofRecord> findByProofId(String proofId);

    /**
     * Finds all delivery proofs for a given mission, ordered chronologically.
     *
     * @param missionId the delivery mission identifier
     * @param tenantId  the tenant identifier
     * @return a {@link Flux} of delivery proofs, oldest first
     */
    Flux<DeliveryProofRecord> findByMissionId(String missionId, String tenantId);

    /**
     * Updates the Fabric transaction hash for a proof after on-chain confirmation.
     * Called by {@link com.yowyob.tiibntick.core.trust.adapter.in.kafka.TrustCommittedEventConsumer}.
     *
     * @param proofId the proof identifier
     * @param txHash  the Fabric transaction hash
     * @return a {@link Mono} completing when the update is persisted
     */
    Mono<Void> updateTxHash(String proofId, String txHash);

    /**
     * Retrieves the most recent confirmed (non-null) blockchain tx hash for a parcel.
     *
     * <p>Used by {@link com.yowyob.tiibntick.core.trust.adapter.out.incident.IncidentBlockchainAuditAdapter}
     * to get the tail hash of a parcel's delivery chain when linking it to an incident chain.
     * Returns the hash of the latest blockchain-confirmed delivery proof for the given parcel.
     *
     * @param parcelId the parcel (package) identifier
     * @return a {@link Mono} emitting the latest confirmed tx hash, or empty if none exists
     */
    Mono<String> findLatestConfirmedHashByParcelId(String parcelId);
}
