package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PacketDTO;
import java.util.UUID;
import lombok.Data;

@Data
public class AnnouncementRequestDTO {
    private UUID clientId;
    private String title;
    private String description;
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
    private String signatureUrl;
    private String paymentMethod;
    private String transportMethod;
    private Double distance;
    private Integer duration;
    private Boolean autoPublish;
    private UUID destinationRelayPointId;
    private Double logisticsPrice;

    /**
     * Type de véhicule souhaité par le client (optionnel).
     * Valeurs acceptées : BIKE, MOTORBIKE, SCOOTER, CAR, VAN, TRUCK.
     * Si null ou absent → tous les types sont acceptés.
     */
    private String requiredVehicleType;

    private AddressDTO pickupAddress;
    private AddressDTO deliveryAddress;
    private PacketDTO packet;
}
