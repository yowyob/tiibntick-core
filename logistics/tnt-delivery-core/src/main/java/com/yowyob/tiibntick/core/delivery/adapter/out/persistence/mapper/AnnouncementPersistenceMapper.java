package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.AnnouncementResponseEntity;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryAnnouncementEntity;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponseStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;

import java.util.List;

/**
 * Manual mapper for {@code DeliveryAnnouncement} ↔ persistence entities.
 *
 * @author MANFOUO Braun
 */
public final class AnnouncementPersistenceMapper {

    private AnnouncementPersistenceMapper() {}

    public static DeliveryAnnouncementEntity toEntity(DeliveryAnnouncement a) {
        DeliveryAddress pickup = a.getPickupAddress();
        DeliveryAddress dest   = a.getDeliveryAddress();
        RecipientInfo   recip  = a.getRecipient();
        return DeliveryAnnouncementEntity.builder()
                .id(a.getId())
                .tenantId(a.getTenantId())
                .clientId(a.getClientId())
                .title(a.getTitle())
                .description(a.getDescription())
                .offeredAmount(a.getOfferedAmount())
                .currency(a.getCurrency())
                .parcelId(a.getParcel() != null ? a.getParcel().getId() : null)
                .status(a.getStatus().name())
                .urgency(a.getUrgency().name())
                .pickupStreet(pickup.street())
                .pickupLandmark(pickup.landmark())
                .pickupDistrict(pickup.district())
                .pickupCity(pickup.city())
                .pickupCountry(pickup.country())
                .pickupLatitude(pickup.coordinates() != null ? pickup.coordinates().latitude() : null)
                .pickupLongitude(pickup.coordinates() != null ? pickup.coordinates().longitude() : null)
                .deliveryStreet(dest.street())
                .deliveryLandmark(dest.landmark())
                .deliveryDistrict(dest.district())
                .deliveryCity(dest.city())
                .deliveryCountry(dest.country())
                .deliveryLatitude(dest.coordinates() != null ? dest.coordinates().latitude() : null)
                .deliveryLongitude(dest.coordinates() != null ? dest.coordinates().longitude() : null)
                .recipientName(recip.name())
                .recipientPhone(recip.phoneNumber())
                .recipientAltPhone(recip.alternativePhone())
                .selectedResponseId(a.getSelectedResponseId())
                .createdDeliveryId(a.getCreatedDeliveryId())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .version(a.getVersion())
                .build();
    }

    public static DeliveryAnnouncement toDomain(DeliveryAnnouncementEntity e,
                                                  Parcel parcel,
                                                  List<AnnouncementResponse> responses) {
        GeoCoordinates pc = coords(e.getPickupLatitude(), e.getPickupLongitude());
        GeoCoordinates dc = coords(e.getDeliveryLatitude(), e.getDeliveryLongitude());

        return DeliveryAnnouncement.builder()
                .id(e.getId())
                .tenantId(e.getTenantId())
                .clientId(e.getClientId())
                .title(e.getTitle())
                .description(e.getDescription())
                .offeredAmount(e.getOfferedAmount())
                .currency(e.getCurrency())
                .parcel(parcel)
                .status(AnnouncementStatus.valueOf(e.getStatus()))
                .urgency(DeliveryUrgency.valueOf(e.getUrgency()))
                .pickupAddress(new DeliveryAddress(e.getPickupStreet(), e.getPickupLandmark(),
                        e.getPickupDistrict(), e.getPickupCity(), e.getPickupCountry(), pc))
                .deliveryAddress(new DeliveryAddress(e.getDeliveryStreet(), e.getDeliveryLandmark(),
                        e.getDeliveryDistrict(), e.getDeliveryCity(), e.getDeliveryCountry(), dc))
                .recipient(new RecipientInfo(e.getRecipientName(), e.getRecipientPhone(),
                        e.getRecipientAltPhone()))
                .responses(responses != null ? new java.util.ArrayList<>(responses) : new java.util.ArrayList<>())
                .selectedResponseId(e.getSelectedResponseId())
                .createdDeliveryId(e.getCreatedDeliveryId())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .version(e.getVersion())
                .build();
    }

    public static AnnouncementResponseEntity responseToEntity(AnnouncementResponse r) {
        return AnnouncementResponseEntity.builder()
                .id(r.getId())
                .announcementId(r.getAnnouncementId())
                .deliveryPersonId(r.getDeliveryPersonId())
                .estimatedArrivalTime(r.getEstimatedArrivalTime())
                .note(r.getNote())
                .status(r.getStatus().name())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .version(r.getVersion())
                .build();
    }

    public static AnnouncementResponse responseToDomain(AnnouncementResponseEntity e) {
        return AnnouncementResponse.builder()
                .id(e.getId())
                .announcementId(e.getAnnouncementId())
                .deliveryPersonId(e.getDeliveryPersonId())
                .estimatedArrivalTime(e.getEstimatedArrivalTime())
                .note(e.getNote())
                .status(ResponseStatus.valueOf(e.getStatus()))
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .version(e.getVersion())
                .build();
    }

    private static GeoCoordinates coords(Double lat, Double lon) {
        if (lat == null || lon == null) return null;
        return new GeoCoordinates(lat, lon);
    }
}
