package com.yowyob.tiibntick.core.agency.assignment.adapter.out.clients;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** Outbound port — delivery lifecycle via platform tnt-delivery-core. */
public interface DeliveryCorePort {

    Mono<DeliveryView> getById(UUID tenantId, UUID deliveryId);

    Mono<DeliveryView> confirmPickup(UUID tenantId, UUID deliveryId);

    Mono<DeliveryView> startTransit(UUID tenantId, UUID deliveryId, Double latitude, Double longitude);

    Mono<DeliveryView> depositAtRelay(UUID tenantId, UUID deliveryId, UUID relayPointId);

    Mono<DeliveryView> complete(UUID tenantId, UUID deliveryId, String proofPhotoUrl);

    Mono<DeliveryView> fail(UUID tenantId, UUID deliveryId, String reason);

    Mono<Void> cancel(UUID tenantId, UUID deliveryId, String reason);

    record DeliveryView(
            UUID id,
            String status,
            Instant actualPickupTime,
            Instant actualDeliveryTime,
            UUID deliveryPersonId) {}
}
