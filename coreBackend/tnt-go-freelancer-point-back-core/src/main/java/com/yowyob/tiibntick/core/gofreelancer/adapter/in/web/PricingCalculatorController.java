package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.PricingCalculatorUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PriceCalculationRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.PriceCalculationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/pricing/calculate")
@RequiredArgsConstructor
public class PricingCalculatorController {

    private final PricingCalculatorUseCase pricingCalculatorUseCase;

    @PostMapping("/freelancer/{id}")
    public Mono<PriceCalculationResponse> calculateFreelancerPrice(
            @PathVariable UUID id,
            @RequestBody PriceCalculationRequest request) {
        return pricingCalculatorUseCase.calculateFreelancerPrice(id, request);
    }

    @PostMapping("/logistics/{id}")
    public Mono<PriceCalculationResponse> calculateLogisticsPrice(
            @PathVariable UUID id,
            @RequestBody PriceCalculationRequest request) {
        return pricingCalculatorUseCase.calculateLogisticsPrice(id, request);
    }
}
