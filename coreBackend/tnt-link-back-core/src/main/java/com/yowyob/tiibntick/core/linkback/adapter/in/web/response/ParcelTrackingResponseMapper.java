package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import com.yowyob.tiibntick.common.kafka.TntEventMasking;
import com.yowyob.tiibntick.common.kafka.TntEventMasking.MaskedAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.linkback.domain.model.LinkPosition;

/** Masked public view of a parcel (D-137): no name, phone, street, id of person, nor exact position. */
public final class ParcelTrackingResponseMapper {

    private ParcelTrackingResponseMapper() {
    }

    public static ParcelTrackingResponse toResponse(String trackingCode, Delivery delivery, LinkPosition currentPosition) {
        return toResponse(trackingCode, delivery, currentPosition, TntEventMasking.withDefaults());
    }

    public static ParcelTrackingResponse toResponse(String trackingCode, Delivery delivery, LinkPosition currentPosition,
                                                    TntEventMasking masking) {
        MaskedAddress pickup = mask(delivery.getPickupAddress(), masking);
        MaskedAddress drop = mask(delivery.getDeliveryAddress(), masking);
        return new ParcelTrackingResponse(
                trackingCode,
                delivery.getStatus().name(),
                delivery.getParcel() != null && delivery.getParcel().getStatus() != null
                        ? delivery.getParcel().getStatus().name() : null,
                pickup != null ? pickup.district() : null,
                pickup != null ? pickup.city() : null,
                pickup != null ? pickup.approxLatitude() : null,
                pickup != null ? pickup.approxLongitude() : null,
                drop != null ? drop.district() : null,
                drop != null ? drop.city() : null,
                drop != null ? drop.approxLatitude() : null,
                drop != null ? drop.approxLongitude() : null,
                delivery.getEstimatedDeliveryTime(),
                delivery.getActualDeliveryTime(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt(),
                currentPosition != null ? masking.approx(currentPosition.latitude()) : null,
                currentPosition != null ? masking.approx(currentPosition.longitude()) : null,
                currentPosition != null ? currentPosition.occurredAt() : null
        );
    }

    private static MaskedAddress mask(DeliveryAddress address, TntEventMasking masking) {
        if (address == null) {
            return null;
        }
        return masking.address(address.district(), address.city(), address.country(),
                address.coordinates() != null ? address.coordinates().latitude() : null,
                address.coordinates() != null ? address.coordinates().longitude() : null);
    }
}
