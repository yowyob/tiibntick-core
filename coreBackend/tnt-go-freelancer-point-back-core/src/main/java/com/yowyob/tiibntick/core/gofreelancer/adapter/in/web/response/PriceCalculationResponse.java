package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculationResponse {
    private Double totalPrice;
    private String currency;
    private String breakdown; // Explication du calcul
}
