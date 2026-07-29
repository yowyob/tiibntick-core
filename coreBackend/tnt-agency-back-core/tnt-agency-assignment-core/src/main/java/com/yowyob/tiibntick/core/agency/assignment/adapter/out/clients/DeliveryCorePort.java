package com.yowyob.tiibntick.core.agency.assignment.adapter.out.clients;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** Outbound port — delivery lifecycle via platform tnt-delivery-core. */
public interface DeliveryCorePort {

    /**
     * Creates a real {@code Delivery} in tnt-delivery-core directly (no announcement/bidding
     * step), for agency-dispatched missions (e.g. from a client intake). The returned
     * {@link DeliveryView#id()} and {@link DeliveryView#trackingCode()} are the real,
     * canonical delivery ID and tracking code — callers should use them as
     * {@code AgencyMission.coreMissionId} / the mission's tracking code instead of
     * fabricating their own.
     */
    Mono<DeliveryView> createDelivery(CreateDeliveryRequest request);

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
            String trackingCode,
            Instant actualPickupTime,
            Instant actualDeliveryTime,
            UUID deliveryPersonId) {}

    record CreateDeliveryRequest(
            UUID tenantId, UUID senderId, UUID agencyId,
            String pickupLandmark, String pickupDistrict, String pickupCity,
            String deliveryLandmark, String deliveryDistrict, String deliveryCity,
            String recipientName, String recipientPhone,
            Double weightKg, Instant scheduledPickupTime) {}
}
