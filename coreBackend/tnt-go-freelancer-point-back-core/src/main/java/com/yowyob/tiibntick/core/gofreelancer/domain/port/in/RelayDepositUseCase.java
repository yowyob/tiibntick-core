package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for relay deposit operations.
 */
public interface RelayDepositUseCase {

    /**
     * Creates a new RelayDeposit when a parcel is deposited at a relay point.
     * Called automatically when a Delivery transitions to DELIVERED with a relayPointId.
     *
     * @param packetId     UUID of the parcel/packet
     * @param clientId     UUID of the client who will pick it up
     * @param relayPointId UUID of the relay point
     * @param storageFee   daily/periodic storage fee (0.0 if free)
     * @return the persisted RelayDeposit
     */
    Mono<RelayDeposit> createRelayDeposit(UUID packetId, UUID clientId, UUID relayPointId, Double storageFee);

    Flux<RelayDeposit> getDepositsByRelayPointId(UUID relayPointId);
    Flux<RelayDeposit> getDepositsByClientId(UUID clientId);
    Mono<RelayDeposit> markAsRetrieved(UUID id, String otpCode);
}
