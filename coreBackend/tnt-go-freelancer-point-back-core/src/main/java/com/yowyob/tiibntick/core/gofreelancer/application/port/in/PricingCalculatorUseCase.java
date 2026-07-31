package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PriceCalculationRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.PriceCalculationResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PricingCalculatorUseCase {
    Mono<PriceCalculationResponse> calculateFreelancerPrice(UUID freelancerId, PriceCalculationRequest request);
    Mono<PriceCalculationResponse> calculateLogisticsPrice(UUID relayPointId, PriceCalculationRequest request);
}
