package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofp.announcements")
public class AnnouncementEntity {

    @Id
    private UUID    id;

    @Column("client_actor_id")
    private UUID    clientActorId;

    @Column("pickup_address_node_id")
    private String  pickupAddressNodeId;

    @Column("delivery_address_node_id")
    private String  deliveryAddressNodeId;

    @Column("relay_hub_id")
    private UUID    relayHubId;

    @Column("destination_logistics_id")
    private UUID    destinationLogisticsId;

    @Column("packet_id")
    private UUID    packetId;

    private String  title;
    private String  description;
    private String  status;
    private Double  amount;

    @Column("logistics_price")
    private Double  logisticsPrice;

    private Double  distance;
    private Integer duration;

    @Column("transport_method")
    private String  transportMethod;

    @Column("payment_method")
    private String  paymentMethod;

    @Column("signature_url")
    private String  signatureUrl;

    @Column("required_vehicle_type")
    private String  requiredVehicleType;

    @Column("auto_publish")
    private Boolean autoPublish;

    // Expéditeur
    @Column("shipper_id")
    private UUID   shipperId;
    @Column("shipper_first_name")
    private String shipperFirstName;
    @Column("shipper_last_name")
    private String shipperLastName;
    @Column("shipper_email")
    private String shipperEmail;
    @Column("shipper_phone")
    private String shipperPhone;

    // Destinataire
    @Column("recipient_id")
    private UUID   recipientId;
    @Column("recipient_first_name")
    private String recipientFirstName;
    @Column("recipient_last_name")
    private String recipientLastName;
    @Column("recipient_email")
    private String recipientEmail;
    @Column("recipient_phone")
    private String recipientPhone;

    // Adresses libres pickup
    @Column("pickup_street")
    private String  pickupStreet;
    @Column("pickup_city")
    private String  pickupCity;
    @Column("pickup_district")
    private String  pickupDistrict;
    @Column("pickup_country")
    private String  pickupCountry;
    @Column("pickup_latitude")
    private Double  pickupLatitude;
    @Column("pickup_longitude")
    private Double  pickupLongitude;

    // Adresses libres delivery
    @Column("delivery_street")
    private String  deliveryStreet;
    @Column("delivery_city")
    private String  deliveryCity;
    @Column("delivery_district")
    private String  deliveryDistrict;
    @Column("delivery_country")
    private String  deliveryCountry;
    @Column("delivery_latitude")
    private Double  deliveryLatitude;
    @Column("delivery_longitude")
    private Double  deliveryLongitude;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
