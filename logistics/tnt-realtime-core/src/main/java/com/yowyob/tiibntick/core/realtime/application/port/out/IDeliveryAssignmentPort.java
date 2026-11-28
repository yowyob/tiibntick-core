package com.yowyob.tiibntick.core.realtime.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Asks the owner of delivery missions whether a user is the one carrying a mission.
 *
 * <p>Realtime does not know deliveries; without this check any authenticated deliverer could push GPS
 * positions into somebody else's mission and corrupt what that mission's client sees. Implemented by
 * a higher layer (gofp / agency) that owns the delivery data; when no implementation is present the
 * ping is accepted as before.
 */
public interface IDeliveryAssignmentPort {

    /**
     * @return {@code true} when {@code userId} is the deliverer assigned to {@code deliveryId} (or the
     *         mission is not a delivery this implementation is responsible for); {@code false} when
     *         the mission exists and belongs to somebody else
     */
    Mono<Boolean> isDeliveryAssignedTo(UUID tenantId, UUID deliveryId, UUID userId);
}
