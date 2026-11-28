package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.time.Instant;

/**
 * Generic Link business representation of a tracked parcel — deliberately not
 * screen-shaped (that adaptation belongs to the Link BFF, not this Core Backend).
 *
 * <p>Lot evt-l2 (D-136, D-137): the code is the parcel's; the lookup is public, so the view is masked like the Kafka
 * payloads — states, district/city, times and coordinates rounded (QO-17). Before: delivery, sender and courier ids,
 * full addresses, exact coordinates, recipient name and phone (see
 * {@code docs/audits/remediation/evenements/README.md}).
 */
public record ParcelTrackingResponse(
        String trackingCode,
        /** State of the current (or last) delivery leg of the parcel. */
        String status,
        /** State of the parcel itself (ParcelStatus). */
        String parcelStatus,
        String pickupDistrict,
        String pickupCity,
        Double pickupLatitude,
        Double pickupLongitude,
        String deliveryDistrict,
        String deliveryCity,
        Double deliveryLatitude,
        Double deliveryLongitude,
        Instant estimatedDeliveryTime,
        Instant actualDeliveryTime,
        Instant createdAt,
        Instant updatedAt,
        /** Live deliverer position materialized from GPS pings (Chantier G, Audit n5 P-17), rounded like the
         *  addresses — {@code null} when no GPS ping has been received for this mission yet. */
        Double currentLatitude,
        Double currentLongitude,
        Instant currentPositionUpdatedAt
) {
}
