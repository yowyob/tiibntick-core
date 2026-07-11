package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;

/**
 * Inbound Port — {@code GetDeliveryAuditTrailUseCase}.
 *
 * <p>Queries the "Fil d'Ariane" — the complete immutable audit trail of a
 * delivery mission or package, combining delivery proofs and custody transfers
 * anchored on Hyperledger Fabric.
 *
 * <p>Results are sourced from both the local PostgreSQL cache (fast path)
 * and the Trust Event REST API (verification path).
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface GetDeliveryAuditTrailUseCase {

    /**
     * Returns all delivery proofs for a given mission, ordered chronologically.
     *
     * @param missionId the delivery mission identifier
     * @param tenantId  the tenant identifier
     * @return a {@link Flux} of delivery proof records, oldest first
     */
    Flux<DeliveryProofRecord> getByMissionId(String missionId, String tenantId);

    /**
     * Returns the complete custody transfer chain for a package,
     * ordered chronologically (oldest first = "Fil d'Ariane").
     *
     * @param trackingCode the package tracking code
     * @param tenantId     the tenant identifier
     * @return a {@link Flux} of custody transfer records
     */
    Flux<CustodyTransferRecord> getByPackageTrackingCode(String trackingCode, String tenantId);

    /**
     * Verifies a delivery proof on-chain by checking the Fabric ledger
     * via the Trust Event REST API.
     *
     * @param txHash       the Fabric transaction hash of the delivery proof
     * @param expectedHash the expected SHA-256 data hash to verify against
     * @return a {@link Mono} emitting {@code true} if the proof is valid
     */
    Mono<Boolean> verifyDeliveryProof(String txHash, String expectedHash);
}
