package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;

/**
 * Inbound Port — {@code RecordCustodyTransferUseCase}.
 *
 * <p>Anchors a package custody transfer on Hyperledger Fabric,
 * contributing to the package's immutable chain of custody ("Fil d'Ariane").
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface RecordCustodyTransferUseCase {

    /**
     * Records a custody transfer on the blockchain.
     *
     * @param transfer the custody transfer record (from/to actor, type, hub)
     * @return a {@link Mono} emitting the Fabric transaction hash on success
     */
    Mono<String> record(CustodyTransferRecord transfer);
}
