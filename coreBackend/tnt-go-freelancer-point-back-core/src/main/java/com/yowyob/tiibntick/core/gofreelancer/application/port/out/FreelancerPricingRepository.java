package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerPricingPolicy;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for freelancer delivery pricing policies.
 *
 * @author MANFOUO BRAUN
 */
public interface FreelancerPricingRepository {

    Mono<FreelancerPricingPolicy> findByDeliveryPersonId(UUID deliveryPersonId);

    Mono<FreelancerPricingPolicy> save(FreelancerPricingPolicy policy);
}
