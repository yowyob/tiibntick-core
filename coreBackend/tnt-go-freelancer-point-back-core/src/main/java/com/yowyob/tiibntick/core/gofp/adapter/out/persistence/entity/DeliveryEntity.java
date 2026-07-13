package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.deliveries")
public class DeliveryEntity {

    @Id private UUID id;

    @Column("announcement_id")     private UUID    announcementId;
    @Column("freelancer_actor_id") private UUID    freelancerActorId;
    @Column("relay_hub_id")        private UUID    relayHubId;

    private String  status;
    private String  urgency;
    private Double  tarif;

    @Column("pickup_min_time")      private Instant pickupMinTime;
    @Column("pickup_max_time")      private Instant pickupMaxTime;
    @Column("delivery_min_time")    private Instant deliveryMinTime;
    @Column("delivery_max_time")    private Instant deliveryMaxTime;
    @Column("estimated_delivery")   private Instant estimatedDelivery;
    @Column("actual_pickup_time")   private Instant actualPickupTime;
    @Column("actual_delivery_time") private Instant actualDeliveryTime;

    @Column("distance_km")          private Double  distanceKm;
    @Column("duration_minutes")     private Integer durationMinutes;
    @Column("note_livreur")         private Double  noteLivreur;
    @Column("delivery_note")        private Double  deliveryNote;
    @Column("delivery_lat")         private Double  deliveryLat;
    @Column("delivery_lon")         private Double  deliveryLon;

    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}
