package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerPricingDTO {
    @Min(0)
    private Double pricePerKg;

    @Min(0)
    private Double pricePerCbm;

    @Min(0)
    private Double pricePerKm;

    @Min(0)
    private Double fragileSurcharge;

    @Min(0)
    private Double perishableSurcharge;

    @Min(0)
    private Double baseFee;

    /**
     * Minimum price of a delivery (the computed price is raised to it). Optional; absent = no minimum
     * (decision D-058, platform grid of {@code docs/architecture/chantiers/tarifs-par-vehicule.md}).
     */
    @Min(0)
    private Double minimumFee;

    /** Price per minute of waiting at pickup/delivery (billed from the declared waiting time). */
    @Min(0)
    private Double waitingFeePerMinute;

    @Builder.Default
    private String currency = "FCFA";

    /** Read-only: the billing policy that carries this grid. Ignored on write. */
    private UUID policyId;

    /** Read-only: {@code POLICY}, {@code TEMPLATE:<code>} or {@code LEGACY}. Ignored on write. */
    private String source;
}
