package com.yowyob.tiibntick.core.delivery.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Who owns a relay hub, for its storage threshold (spec §5, QO-29): an agency relay takes its organisation's value, an
 * independent relay its own value within the platform bounds (D-130).
 *
 * <p>Implemented by the modules that register relays (agency back-core for agency relays, GOFP for independent
 * relays). Without an answer the platform value applies.
 */
public interface RelayOwnershipPort {

    /**
     * @param ownerKernelOrganizationId Kernel organisation owning the relay, {@code null} for an independent relay
     * @param independentStorageDays    storage set by an independent relay, {@code null} when none
     */
    record RelayOwnership(UUID ownerKernelOrganizationId, Integer independentStorageDays) {
    }

    /** @return the ownership of the hub, empty when this implementation does not know it */
    Mono<RelayOwnership> findByHubId(UUID hubId);
}
