package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Freelancer pricing policy CRUD.
 *
 * @author MANFOUO BRAUN
 */
public interface FreelancerPricingPolicyUseCase {

    Mono<FreelancerPricingDTO> getPolicy(UUID freelancerId);

    Mono<FreelancerPricingDTO> upsertPolicy(UUID freelancerId, FreelancerPricingDTO request);
}
