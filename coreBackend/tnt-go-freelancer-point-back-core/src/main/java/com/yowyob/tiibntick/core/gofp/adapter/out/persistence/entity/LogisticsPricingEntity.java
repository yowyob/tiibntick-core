package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.logistics_pricing")
public class LogisticsPricingEntity {

    @Id private UUID id;

    @Column("relay_hub_id")          private UUID    relayHubId;
    @Column("price_per_kg")          private Double  pricePerKg;
    @Column("price_per_cbm")         private Double  pricePerCbm;
    @Column("price_per_day")         private Double  pricePerDay;
    @Column("grace_period_days")     private Integer gracePeriodDays;
    @Column("penalty_per_day")       private Double  penaltyPerDay;
    @Column("fragile_surcharge")     private Double  fragileSurcharge;
    @Column("perishable_surcharge")  private Double  perishableSurcharge;
    @Column("base_fee")              private Double  baseFee;
    private String currency;

    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}
