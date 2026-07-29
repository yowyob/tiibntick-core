package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    
    @Builder.Default
    private String currency = "FCFA";
}
