package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port to the delivery lifecycle owned by {@code tnt-delivery-core}.
 *
 * <p>The delivery aggregate (status, transitions, tracking code, pickup/delivery instants and the
 * domain events other modules react to) lives in tnt-delivery-core, which is the single source of
 * truth. gofp keeps only what is specific to the Go-Freelancer product on its own row (OTP hashes,
 * receive code, fare, time windows) and drives the lifecycle exclusively through this port, so an
 * illegal transition is refused by the core's state machine and every accepted one is published.
 *
 * @author MANFOUO Braun
 */
public interface IDeliveryLifecyclePort {

    /** What the core currently says about a delivery. */
    record CoreDeliveryState(
            UUID deliveryId,
            String status,
            String trackingCode,
            UUID deliveryPersonId,
            Instant actualPickupTime,
            Instant actualDeliveryTime) {
    }

    Mono<CoreDeliveryState> findState(UUID tenantId, UUID deliveryId);

    /** CREATED -> PICKED_UP. */
    Mono<CoreDeliveryState> confirmPickup(UUID tenantId, UUID deliveryId, UUID deliveryPersonId);

    /** PICKED_UP -> IN_TRANSIT, or AT_RELAY_POINT -> IN_TRANSIT (resume after a relay stop). */
    Mono<CoreDeliveryState> startOrResumeTransit(UUID tenantId, UUID deliveryId, UUID deliveryPersonId);

    /** IN_TRANSIT -> AT_RELAY_POINT. */
    Mono<CoreDeliveryState> depositAtRelayPoint(UUID tenantId, UUID deliveryId, UUID deliveryPersonId,
                                                UUID relayPointId);

    /** IN_TRANSIT or AT_RELAY_POINT -> DELIVERED. */
    Mono<CoreDeliveryState> complete(UUID tenantId, UUID deliveryId, UUID deliveryPersonId);

    Mono<CoreDeliveryState> fail(UUID tenantId, UUID deliveryId, UUID deliveryPersonId, String reason);

    Mono<CoreDeliveryState> cancel(UUID tenantId, UUID deliveryId, UUID requesterId, String reason);
}
