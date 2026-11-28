package com.yowyob.tiibntick.core.delivery.adapter.in.web.response;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;

import java.time.Instant;
import java.util.List;

/**
 * Public tracking view of a parcel (D-136, D-137): what anyone holding the code may see — states, cities and times,
 * nothing else. No name, phone, street, landmark, exact position, courier, amount or code: the detailed view needs
 * an authenticated call.
 *
 * @param trackingCode the parcel code
 * @param parcelStatus state of the parcel ({@code ParcelStatus})
 * @param originCity   city of the first pickup
 * @param destinationCity city of the current destination
 * @param registeredAt when the parcel was registered
 * @param updatedAt    last change of the parcel
 * @param history      every delivery of the parcel (outbound, return, redelivery), oldest first
 */
public record PublicTrackingResponse(String trackingCode, String parcelStatus, String originCity,
                                     String destinationCity, Instant registeredAt, Instant updatedAt,
                                     List<Leg> history) {

    /**
     * One delivery of the parcel, masked.
     *
     * @param kind         OUTBOUND, RETURN or REDELIVERY
     * @param status       state of the delivery
     * @param fromCity     pickup city
     * @param toCity       destination city
     * @param createdAt    creation
     * @param pickedUpAt   pickup, may be {@code null}
     * @param estimatedAt  current ETA, may be {@code null}
     * @param deliveredAt  hand-over, may be {@code null}
     * @param updatedAt    last change
     */
    public record Leg(String kind, String status, String fromCity, String toCity, Instant createdAt,
                      Instant pickedUpAt, Instant estimatedAt, Instant deliveredAt, Instant updatedAt) {

        static Leg of(Delivery d) {
            return new Leg(d.getKind().name(), d.getStatus().name(),
                    d.getPickupAddress() != null ? d.getPickupAddress().city() : null,
                    d.getDeliveryAddress() != null ? d.getDeliveryAddress().city() : null,
                    d.getCreatedAt(), d.getActualPickupTime(),
                    d.getCurrentEta() != null ? d.getCurrentEta().estimatedArrival() : null,
                    d.getActualDeliveryTime(), d.getUpdatedAt());
        }
    }

    /** Builds the view from the deliveries of one parcel, oldest first (not empty). */
    public static PublicTrackingResponse of(String trackingCode, List<Delivery> deliveries) {
        Delivery first = deliveries.getFirst();
        Delivery last = deliveries.getLast();
        Parcel parcel = last.getParcel();
        return new PublicTrackingResponse(trackingCode,
                parcel != null ? parcel.getStatus().name() : null,
                first.getPickupAddress() != null ? first.getPickupAddress().city() : null,
                last.getDeliveryAddress() != null ? last.getDeliveryAddress().city() : null,
                parcel != null ? parcel.getCreatedAt() : first.getCreatedAt(),
                parcel != null && parcel.getUpdatedAt() != null ? parcel.getUpdatedAt() : last.getUpdatedAt(),
                deliveries.stream().map(Leg::of).toList());
    }
}
