package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.AnnouncementResponseEntity;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryAnnouncementEntity;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementCreatorRole;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementOutcome;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementPricingMode;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponseStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.AnnouncementDetails;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.SenderInfo;

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
        SenderInfo      sender = a.getSender();
        AnnouncementDetails d  = a.getDetails() != null ? a.getDetails() : AnnouncementDetails.EMPTY;
        return DeliveryAnnouncementEntity.builder()
                .id(a.getId())
                .tenantId(a.getTenantId())
                .clientId(a.getClientId())
                .title(a.getTitle())
                .description(a.getDescription())
                .offeredAmount(a.getOfferedAmount())
                .currency(a.getCurrency())
                .pricingMode(a.getPricingMode() != null ? a.getPricingMode().name() : AnnouncementPricingMode.FIXED_PRICE.name())
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
                // lot evt-l2: a RETURN draft whose sender contact is unknown has no recipient yet (filled before A2)
                .recipientName(recip != null ? recip.name() : null)
                .recipientPhone(recip != null ? recip.phoneNumber() : null)
                .recipientAltPhone(recip != null ? recip.alternativePhone() : null)
                .recipientEmail(recip != null ? recip.email() : null)
                .senderName(sender != null ? sender.name() : null)
                .senderPhone(sender != null ? sender.phoneNumber() : null)
                .senderEmail(sender != null ? sender.email() : null)
                .requiredVehicleType(d.requiredVehicleType())
                .paymentMethod(d.paymentMethod())
                .transportMethod(d.transportMethod())
                .distanceKm(d.distanceKm())
                .estimatedDurationMin(d.estimatedDurationMin())
                .destinationHubId(d.destinationHubId())
                .handoverSignatureRef(d.handoverSignatureRef())
                .serviceFee(d.serviceFee())
                .originRefType(d.originRefType())
                .originRefId(d.originRefId())
                .selectedResponseId(a.getSelectedResponseId())
                .createdDeliveryId(a.getCreatedDeliveryId())
                .kind(a.getKind().name())
                .creatorRole(a.getCreatorRole().name())
                .outcome(a.getOutcome() != null ? a.getOutcome().name() : null)
                .publishedAt(a.getPublishedAt())
                .assignedAt(a.getAssignedAt())
                .closedAt(a.getClosedAt())
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

        AnnouncementPricingMode pricingMode = e.getPricingMode() != null
                ? AnnouncementPricingMode.valueOf(e.getPricingMode())
                : AnnouncementPricingMode.FIXED_PRICE;

        return DeliveryAnnouncement.builder()
                .id(e.getId())
                .tenantId(e.getTenantId())
                .clientId(e.getClientId())
                .title(e.getTitle())
                .description(e.getDescription())
                .offeredAmount(e.getOfferedAmount())
                .currency(e.getCurrency())
                .pricingMode(pricingMode)
                .parcel(parcel)
                .status(AnnouncementStatus.valueOf(e.getStatus()))
                .urgency(DeliveryUrgency.valueOf(e.getUrgency()))
                .pickupAddress(new DeliveryAddress(e.getPickupStreet(), e.getPickupLandmark(),
                        e.getPickupDistrict(), e.getPickupCity(), e.getPickupCountry(), pc))
                .deliveryAddress(new DeliveryAddress(e.getDeliveryStreet(), e.getDeliveryLandmark(),
                        e.getDeliveryDistrict(), e.getDeliveryCity(), e.getDeliveryCountry(), dc))
                .recipient(e.getRecipientName() == null || e.getRecipientPhone() == null ? null
                        : new RecipientInfo(e.getRecipientName(), e.getRecipientPhone(),
                                e.getRecipientAltPhone(), e.getRecipientEmail()))
                .sender(sender(e))
                .details(new AnnouncementDetails(e.getRequiredVehicleType(), e.getPaymentMethod(),
                        e.getTransportMethod(), e.getDistanceKm(), e.getEstimatedDurationMin(),
                        e.getDestinationHubId(), e.getHandoverSignatureRef(), e.getServiceFee(),
                        e.getOriginRefType(), e.getOriginRefId()))
                .responses(responses != null ? new java.util.ArrayList<>(responses) : new java.util.ArrayList<>())
                .selectedResponseId(e.getSelectedResponseId())
                .createdDeliveryId(e.getCreatedDeliveryId())
                .kind(e.getKind() != null ? AnnouncementKind.valueOf(e.getKind()) : AnnouncementKind.OUTBOUND)
                .creatorRole(e.getCreatorRole() != null ? AnnouncementCreatorRole.valueOf(e.getCreatorRole())
                        : AnnouncementCreatorRole.CLIENT)
                .outcome(e.getOutcome() != null ? AnnouncementOutcome.valueOf(e.getOutcome()) : null)
                .publishedAt(e.getPublishedAt())
                .assignedAt(e.getAssignedAt())
                .closedAt(e.getClosedAt())
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
                .proposedPrice(r.getProposedPrice())
                .proposedCurrency(r.getProposedCurrency())
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
                .proposedPrice(e.getProposedPrice())
                .proposedCurrency(e.getProposedCurrency())
                .status(ResponseStatus.valueOf(e.getStatus()))
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .version(e.getVersion())
                .build();
    }

    private static SenderInfo sender(DeliveryAnnouncementEntity e) {
        SenderInfo s = new SenderInfo(e.getSenderName(), e.getSenderPhone(), e.getSenderEmail());
        return s.isEmpty() ? null : s;
    }

    private static GeoCoordinates coords(Double lat, Double lon) {
        if (lat == null || lon == null) return null;
        return new GeoCoordinates(lat, lon);
    }
}
