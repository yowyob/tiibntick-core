package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PacketDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementExtras;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Translations of the core announcement ({@link AnnouncementSnapshot}) into the shapes the
 * Go-Freelancer API and services expose. Single place where the core's folded names are split back
 * into first / last name.
 */
public final class AnnouncementViews {

    private AnnouncementViews() {
    }

    /** First word / the rest of a folded name; {@code null} when absent. */
    public static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String t = fullName.trim();
        int i = indexOfBlank(t);
        return i < 0 ? t : t.substring(0, i);
    }

    public static String lastName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String t = fullName.trim();
        int i = indexOfBlank(t);
        return i < 0 ? null : t.substring(i + 1).trim();
    }

    public static String joinName(String first, String last) {
        String f = first != null ? first.trim() : "";
        String l = last != null ? last.trim() : "";
        return (f + " " + l).trim();
    }

    private static int indexOfBlank(String t) {
        for (int i = 0; i < t.length(); i++) {
            if (Character.isWhitespace(t.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static Double d(BigDecimal v) {
        return v != null ? v.doubleValue() : null;
    }

    /** The announcement as the REST DTO (no candidate-specific data, no delivery code). */
    public static AnnouncementResponseDTO toDto(AnnouncementSnapshot a) {
        AnnouncementExtras x = a.extras();
        AnnouncementResponseDTO dto = new AnnouncementResponseDTO();
        dto.setId(a.id());
        dto.setClientId(a.clientId());
        dto.setTitle(a.title());
        dto.setDescription(a.description());
        dto.setStatus(a.status());
        dto.setCreatedAt(a.createdAt());
        dto.setUpdatedAt(a.updatedAt());
        dto.setAmount(d(a.offeredAmount()));
        dto.setCurrency(a.currency());
        dto.setPricingMode(a.pricingMode());
        dto.setRecipientFirstName(firstName(a.recipientName()));
        dto.setRecipientLastName(lastName(a.recipientName()));
        dto.setRecipientEmail(x.recipientEmail());
        dto.setRecipientPhone(a.recipientPhone());
        dto.setShipperFirstName(firstName(x.senderName()));
        dto.setShipperLastName(lastName(x.senderName()));
        dto.setShipperEmail(x.senderEmail());
        dto.setShipperPhone(x.senderPhone());
        dto.setSignatureUrl(x.handoverSignatureRef());
        dto.setPaymentMethod(x.paymentMethod());
        dto.setTransportMethod(x.transportMethod());
        dto.setDistance(d(x.distanceKm()));
        dto.setDuration(x.estimatedDurationMin());
        dto.setLogisticsPrice(d(x.serviceFee()));
        dto.setRequiredVehicleType(x.requiredVehicleType());
        dto.setDestinationRelayPointId(x.destinationHubId());
        dto.setDeliveryId(a.createdDeliveryId());
        dto.setTrackingCode(a.trackingCode());
        if (a.pickupAddress() != null) {
            dto.setPickupAddress(AddressDTO.builder().address(a.pickupAddress()).build());
        }
        if (a.deliveryAddress() != null) {
            dto.setDeliveryAddress(AddressDTO.builder().address(a.deliveryAddress()).build());
        }
        dto.setPacket(toPacketDto(a));
        UUID assigned = selectedDeliveryPerson(a);
        if (assigned != null) {
            dto.setAssignedFreelancerId(assigned);
        }
        return dto;
    }

    public static UUID selectedDeliveryPerson(AnnouncementSnapshot a) {
        if (a.selectedResponseId() == null) {
            return null;
        }
        return a.responses().stream()
                .filter(r -> r.id().equals(a.selectedResponseId()))
                .map(r -> r.deliveryPersonId())
                .findFirst()
                .orElse(null);
    }

    private static PacketDTO toPacketDto(AnnouncementSnapshot a) {
        if (a.packetWeightKg() == null && a.packetWidthCm() == null && a.packetDescription() == null
                && a.packetPhotoUrl() == null && a.packetVolumetricWeightDm3() <= 0) {
            return null;
        }
        PacketDTO packet = new PacketDTO();
        packet.setWeight(a.packetWeightKg());
        packet.setWidth(a.packetWidthCm());
        packet.setHeight(a.packetHeightCm());
        packet.setLength(a.packetLengthCm());
        packet.setFragile(a.packetFragile());
        packet.setIsPerishable(a.packetPerishable());
        packet.setDescription(a.packetDescription());
        packet.setPhotoPacket(a.packetPhotoUrl());
        return packet;
    }

    /** The read-only {@link Announcement} view legacy callers expect. */
    public static Announcement toLocal(AnnouncementSnapshot a) {
        AnnouncementExtras x = a.extras();
        return Announcement.builder()
                .id(a.id())
                .clientId(a.clientId())
                .title(a.title())
                .description(a.description())
                .status(a.status())
                .duration(x.estimatedDurationMin())
                .distance(d(x.distanceKm()))
                .transportMethod(x.transportMethod())
                .requiredVehicleType(x.requiredVehicleType())
                .assignedFreelancerId(selectedDeliveryPerson(a))
                .deliveryNeedId(AnnouncementExtras.ORIGIN_DELIVERY_NEED.equals(x.originRefType())
                        ? x.originRefId() : null)
                .destinationRelayPointId(x.destinationHubId())
                .shipperFirstName(firstName(x.senderName()))
                .shipperLastName(lastName(x.senderName()))
                .shipperEmail(x.senderEmail())
                .shipperPhone(x.senderPhone())
                .recipientFirstName(firstName(a.recipientName()))
                .recipientLastName(lastName(a.recipientName()))
                .recipientEmail(x.recipientEmail())
                .recipientPhone(a.recipientPhone())
                .amount(d(a.offeredAmount()))
                .currency(a.currency())
                .logisticsPrice(d(x.serviceFee()))
                .paymentMethod(x.paymentMethod())
                .signatureUrl(x.handoverSignatureRef())
                .createdAt(a.createdAt())
                .updatedAt(a.updatedAt())
                .build();
    }
}
