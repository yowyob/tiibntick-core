package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PacketDTO;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * DTO representing the response for an announcement.
 *
 * @author François-Charles ATANGA
 * @date 03/02/2026
 *       Note: Added explicit setters for complex objects (pickupAddress,
 *       deliveryAddress, packet)
 *       to resolve mapping issues where Lombok setters were not being detected
 *       correctly.
 */
@Data
public class AnnouncementResponseDTO {
    private UUID id;
    private UUID clientId;
    private String title;
    private String description;
    private AnnouncementStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private String recipientFirstName;
    private String recipientLastName;
    private String recipientEmail;
    private String recipientPhone;
    private String shipperFirstName;
    private String shipperLastName;
    private String shipperEmail;
    private String shipperPhone;
    private Double amount;
    private String currency;
    private String pricingMode;
    private String signatureUrl;
    private String paymentMethod;
    private String transportMethod;
    private Double distance;
    private Integer duration;
    private UUID destinationRelayPointId;
    private Double logisticsPrice;

    /**
     * Type de véhicule requis par le client (optionnel).
     * Null si le client accepte tous les types.
     */
    private String requiredVehicleType;

    private AddressDTO pickupAddress;
    private AddressDTO deliveryAddress;
    private PacketDTO packet;

    // Assigned delivery person info (populated when status is ASSIGNED)
    private UUID assignedFreelancerId;
    private String assignedFreelancerFirstName;
    private String assignedFreelancerLastName;
    private String assignedFreelancerEmail;
    private String assignedFreelancerPhone;

    public void setPickupAddress(AddressDTO pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public void setDeliveryAddress(AddressDTO deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public void setPacket(PacketDTO packet) {
        this.packet = packet;
    }
}
