package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.delivery_needs")
public class DeliveryNeedEntity {

    @Id private UUID id;

    @Column("client_actor_id")      private UUID    clientActorId;
    @Column("packet_id")            private UUID    packetId;
    @Column("delivery_id")          private UUID    deliveryId;

    @Column("pickup_street")        private String  pickupStreet;
    @Column("pickup_city")          private String  pickupCity;
    @Column("pickup_district")      private String  pickupDistrict;
    @Column("pickup_country")       private String  pickupCountry;
    @Column("pickup_latitude")      private Double  pickupLatitude;
    @Column("pickup_longitude")     private Double  pickupLongitude;

    @Column("delivery_street")      private String  deliveryStreet;
    @Column("delivery_city")        private String  deliveryCity;
    @Column("delivery_district")    private String  deliveryDistrict;
    @Column("delivery_country")     private String  deliveryCountry;
    @Column("delivery_latitude")    private Double  deliveryLatitude;
    @Column("delivery_longitude")   private Double  deliveryLongitude;

    private String  title;
    private String  description;
    private String  status;
    private Integer duration;

    @Column("signature_url")        private String  signatureUrl;
    @Column("payment_method")       private String  paymentMethod;
    @Column("transport_method")     private String  transportMethod;
    private Double  distance;

    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}
