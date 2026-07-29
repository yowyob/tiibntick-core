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
    private Double distance;
    private Integer duration;
    private java.time.LocalDateTime pickupDeadline;
    
    // Optional target relay point ID
    private UUID targetRelayPointId;
    
    // Number of days the client wants to keep the parcel at the relay point
    private Integer requestedStorageDays;

    // Address & Packet DTOs
    private AddressDTO pickupAddress;
    private AddressDTO deliveryAddress;
    private PacketDTO packet;
}
