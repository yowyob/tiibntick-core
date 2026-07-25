package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic Link business representation of a tracked parcel — deliberately not
 * screen-shaped (that adaptation belongs to the Link BFF, not this Core Backend).
 */
public record ParcelTrackingResponse(
        UUID deliveryId,
        String trackingCode,
        String status,
        UUID senderId,
        UUID deliveryPersonId,
        String pickupAddress,
        Double pickupLatitude,
        Double pickupLongitude,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        String recipientName,
        String recipientPhone,
        Instant estimatedDeliveryTime,
        Instant actualDeliveryTime,
        Instant createdAt,
        Instant updatedAt,
        /** Live deliverer position materialized from GPS pings (Chantier G, Audit n5 P-17) —
         *  {@code null} when no GPS ping has been received for this mission yet, distinct from
         *  the fixed {@code pickup}/{@code delivery} coordinates above. */
        Double currentLatitude,
        Double currentLongitude,
        Instant currentPositionUpdatedAt
) {
}
