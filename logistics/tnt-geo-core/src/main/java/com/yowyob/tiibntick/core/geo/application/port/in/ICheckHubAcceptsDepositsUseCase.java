package com.yowyob.tiibntick.core.geo.application.port.in;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port (D-140): a hub that is not APPROVED refuses every new deposit. Used by the inventory before recording
 * a deposit. A hub unknown to geo-core (e.g. an Agency hub, managed by the Agency platform: D-143) is not governed
 * here: the check completes empty.
 */
public interface ICheckHubAcceptsDepositsUseCase {

    /**
     * @return empty when the hub accepts deposits or is unknown to geo-core;
     *         {@code RelayHubStateException} when it is PENDING, SUSPENDED, REJECTED or REVOKED
     */
    Mono<Void> requireAcceptsDeposits(UUID hubId);
}
