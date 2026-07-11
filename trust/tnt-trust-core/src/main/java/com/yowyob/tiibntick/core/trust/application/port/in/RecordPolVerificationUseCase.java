package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;

/**
 * Inbound Port — {@code RecordPolVerificationUseCase}.
 *
 * <p>Anchors a Proof-of-Location verification on Hyperledger Fabric.
 * Used to provide cryptographic evidence that a deliverer was physically
 * present at a delivery location at a given timestamp.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface RecordPolVerificationUseCase {

    /**
     * Records a verified Proof-of-Location on the blockchain.
     *
     * @param actorId  the deliverer's actor identifier
     * @param gpsLat   the verified GPS latitude
     * @param gpsLng   the verified GPS longitude
     * @param polHash  the SHA-256 hash of the PoL payload from the mobile app
     * @param tenantId the tenant identifier
     * @return a {@link Mono} emitting the Fabric transaction hash
     */
    Mono<String> record(String actorId, double gpsLat, double gpsLng,
                        String polHash, String tenantId);
}
