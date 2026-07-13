package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;

public final class ParcelTrackingResponseMapper {

    private ParcelTrackingResponseMapper() {
    }

    public static ParcelTrackingResponse toResponse(String trackingCode, Delivery delivery) {
        GeoCoordinates pickupCoords = coordinatesOf(delivery.getPickupAddress());
        GeoCoordinates deliveryCoords = coordinatesOf(delivery.getDeliveryAddress());
        return new ParcelTrackingResponse(
                delivery.getId(),
                trackingCode,
                delivery.getStatus().name(),
                delivery.getSenderId(),
                delivery.getDeliveryPersonId(),
                delivery.getPickupAddress() != null ? delivery.getPickupAddress().toDisplayString() : null,
                pickupCoords != null ? pickupCoords.latitude() : null,
                pickupCoords != null ? pickupCoords.longitude() : null,
                delivery.getDeliveryAddress() != null ? delivery.getDeliveryAddress().toDisplayString() : null,
                deliveryCoords != null ? deliveryCoords.latitude() : null,
                deliveryCoords != null ? deliveryCoords.longitude() : null,
                delivery.getRecipient() != null ? delivery.getRecipient().name() : null,
                delivery.getRecipient() != null ? delivery.getRecipient().phoneNumber() : null,
                delivery.getEstimatedDeliveryTime(),
                delivery.getActualDeliveryTime(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }

    private static GeoCoordinates coordinatesOf(DeliveryAddress address) {
        return address != null ? address.coordinates() : null;
    }
}
