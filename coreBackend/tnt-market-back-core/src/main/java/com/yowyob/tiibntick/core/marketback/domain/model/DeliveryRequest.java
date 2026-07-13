package com.yowyob.tiibntick.core.marketback.domain.model;

import java.time.LocalDateTime;

/**
 * Value Object — client delivery request embedded in a QuoteRequest or MarketOrder.
 * @author MANFOUO Braun
 */
public record DeliveryRequest(
        Address pickupAddress,
        Address deliveryAddress,
        ParcelSpec parcelSpec,
        LocalDateTime desiredPickupAt,
        LocalDateTime desiredDeliveryAt,
        DeliveryUrgency urgency,
        String specialInstructions
) {
    /** Haversine estimate of distance between pickup and delivery. */
    public double distanceKm() {
        if (!pickupAddress.hasCoordinates() || !deliveryAddress.hasCoordinates()) return 0.0;
        double lat1 = pickupAddress.lat();
        double lng1 = pickupAddress.lng();
        double lat2 = deliveryAddress.lat();
        double lng2 = deliveryAddress.lng();
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public boolean isExpressRequired() {
        return urgency == DeliveryUrgency.EXPRESS || urgency == DeliveryUrgency.SAME_DAY;
    }
}
