package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.delivery_person_pricing")
public class DeliveryPersonPricingEntity {

    @Id private UUID id;

    @Column("freelancer_actor_id")   private UUID   freelancerActorId;
    @Column("price_per_kg")          private Double pricePerKg;
    @Column("price_per_cbm")         private Double pricePerCbm;
    @Column("price_per_km")          private Double pricePerKm;
    @Column("fragile_surcharge")     private Double fragileSurcharge;
    @Column("perishable_surcharge")  private Double perishableSurcharge;
    @Column("base_fee")              private Double baseFee;
    private String currency;

    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}
