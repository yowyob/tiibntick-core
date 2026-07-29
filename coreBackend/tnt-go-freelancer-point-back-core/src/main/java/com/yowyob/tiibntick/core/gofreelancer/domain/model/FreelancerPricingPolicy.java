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
 * Entité représentant la grille tarifaire d'un Freelancer (Delivery Person).
 * Elle permet à chaque livreur de fixer ses propres tarifs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("delivery_person_pricing")
public class FreelancerPricingPolicy {

    @Id
    @Column("id")
    private UUID id;

    @Column("delivery_person_id")
    private UUID deliveryPersonId;

    @Column("price_per_kg")
    private Double pricePerKg;

    @Column("price_per_cbm")
    private Double pricePerCbm;

    @Column("price_per_km")
    private Double pricePerKm;

    @Column("fragile_surcharge")
    private Double fragileSurcharge;

    @Column("perishable_surcharge")
    private Double perishableSurcharge;

    @Column("base_fee")
    private Double baseFee;

    @Column("currency")
    private String currency;
}
