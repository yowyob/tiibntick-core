package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.delivery_person_availability")
public class DeliveryPersonAvailabilityEntity {

    @Id private UUID id;

    @Column("freelancer_actor_id")  private UUID    freelancerActorId;
    @Column("is_available")         private Boolean isAvailable;
    @Column("availability_start")   private Instant availabilityStart;
    @Column("availability_end")     private Instant availabilityEnd;
    @Column("current_lat")          private Double  currentLat;
    @Column("current_lon")          private Double  currentLon;
    @Column("location_updated_at")  private Instant locationUpdatedAt;
    @Column("service_zone_id")      private UUID    serviceZoneId;

    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}
