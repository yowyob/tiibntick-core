package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerEligibility;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound port for freelancer eligibility (availability, accepted packages, weight range). */
public interface FreelancerEligibilityRepository {

    Mono<FreelancerEligibility> findByDeliveryPersonId(UUID deliveryPersonId);

    Mono<FreelancerEligibility> save(FreelancerEligibility eligibility);
}
