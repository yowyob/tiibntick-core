package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;

public final class BoardEntryResponseMapper {

    private BoardEntryResponseMapper() {
    }

    public static BoardEntryResponse toResponse(DeliveryAnnouncement announcement) {
        var responses = announcement.getResponses().stream()
                .map(BoardEntryResponseMapper::toResponseSummary)
                .toList();
        GeoCoordinates pickupCoords = coordinatesOf(announcement.getPickupAddress());
        GeoCoordinates deliveryCoords = coordinatesOf(announcement.getDeliveryAddress());
        return new BoardEntryResponse(
                announcement.getId(),
                announcement.getClientId(),
                announcement.getTitle(),
                announcement.getDescription(),
                announcement.getOfferedAmount(),
                announcement.getCurrency(),
                announcement.getParcel() != null ? announcement.getParcel().getSpecification().weightKg() : null,
                announcement.getPickupAddress() != null ? announcement.getPickupAddress().toDisplayString() : null,
                pickupCoords != null ? pickupCoords.latitude() : null,
                pickupCoords != null ? pickupCoords.longitude() : null,
                announcement.getDeliveryAddress() != null ? announcement.getDeliveryAddress().toDisplayString() : null,
                deliveryCoords != null ? deliveryCoords.latitude() : null,
                deliveryCoords != null ? deliveryCoords.longitude() : null,
                announcement.getRecipient() != null ? announcement.getRecipient().name() : null,
                announcement.getUrgency() != null ? announcement.getUrgency().name() : null,
                announcement.getStatus().name(),
                responses,
                announcement.getSelectedResponseId(),
                announcement.getCreatedDeliveryId(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt()
        );
    }

    private static GeoCoordinates coordinatesOf(DeliveryAddress address) {
        return address != null ? address.coordinates() : null;
    }

    private static BoardResponseSummary toResponseSummary(AnnouncementResponse response) {
        return new BoardResponseSummary(
                response.getId(),
                response.getDeliveryPersonId(),
                response.getEstimatedArrivalTime(),
                response.getNote(),
                response.getStatus().name()
        );
    }
}
