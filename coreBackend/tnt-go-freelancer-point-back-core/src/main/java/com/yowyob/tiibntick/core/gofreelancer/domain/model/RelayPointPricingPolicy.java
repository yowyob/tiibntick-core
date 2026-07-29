package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité représentant la grille tarifaire d'un Point Relais (Logistics).
 * Elle permet à chaque point relais de facturer le stockage journalier.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("logistics_pricing")
public class RelayPointPricingPolicy {

    @Id
    @Column("id")
    private UUID id;

    @Column("logistics_id")
    private UUID logisticsId;

    @Column("price_per_kg")
    private Double pricePerKg;

    @Column("price_per_cbm")
    private Double pricePerCbm;

    @Column("price_per_day")
    private Double pricePerDay;

    @Column("grace_period_days")
    private Integer gracePeriodDays;

    @Column("penalty_per_day")
    private Double penaltyPerDay;

    @Column("fragile_surcharge")
    private Double fragileSurcharge;

    @Column("perishable_surcharge")
    private Double perishableSurcharge;

    @Column("base_fee")
    private Double baseFee;

    @Column("currency")
    private String currency;
}
