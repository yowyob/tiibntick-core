package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;

/**
 * Inbound Port — {@code RecordDeliveryProofUseCase}.
 *
 * <p>Anchors a delivery proof on Hyperledger Fabric via {@code yow-trust-event}.
 * Returns the Fabric transaction hash upon successful commit.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface RecordDeliveryProofUseCase {

    /**
     * Records a delivery proof on the blockchain.
     *
     * @param proof the delivery proof record (photo hash, GPS, signature)
     * @return a {@link Mono} emitting the Fabric transaction hash on success
     */
    Mono<String> record(DeliveryProofRecord proof);
}
