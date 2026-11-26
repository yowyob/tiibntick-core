package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PacketDTO;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryNeedRequestDTO {
    private UUID userId;
    private String title;
    private String description;
    private String signatureUrl;
    private String paymentMethod;
    private String transportMethod;
    /** Lot C-54 — moto, tricycle, taxi, camion, bus (or the core type names); absent / "ANY" = any vehicle. */
    private String requiredVehicleType;
    private Double distance;
    private Integer duration;
    private java.time.LocalDateTime pickupDeadline;
    
    // Optional target relay point ID
    private UUID targetRelayPointId;
    
    // Number of days the client wants to keep the parcel at the relay point
    private Integer requestedStorageDays;

    // Address & Packet DTOs
    private AddressDTO pickupAddress;

    // Lot C-56 — contacts typed by the sender: kept on the need, shown to the elected courier.
    private String senderFirstName;
    private String senderLastName;
    private String senderPhone;
    private String recipientFirstName;
    private String recipientLastName;
    private String recipientPhone;
    private AddressDTO deliveryAddress;
    private PacketDTO packet;
}
