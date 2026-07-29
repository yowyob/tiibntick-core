package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculationRequest {
    @Builder.Default
    private Double weight = 0.0;
    @Builder.Default
    private Double volumeCbm = 0.0;
    @Builder.Default
    private Boolean isFragile = false;
    @Builder.Default
    private Boolean isPerishable = false;
    @Builder.Default
    private Double distanceKm = 0.0; // Pour le livreur
    @Builder.Default
    private Integer days = 1; // Pour le point relais (1 jour par défaut)
}
