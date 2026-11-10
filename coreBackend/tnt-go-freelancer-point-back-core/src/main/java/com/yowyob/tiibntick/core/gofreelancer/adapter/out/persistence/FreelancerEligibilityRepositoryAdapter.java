package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerEligibilityR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.FreelancerEligibilityRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerEligibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FreelancerEligibilityRepositoryAdapter implements FreelancerEligibilityRepository {

    private final FreelancerEligibilityR2dbcRepository r2dbcRepository;

    @Override
    public Mono<FreelancerEligibility> findByDeliveryPersonId(UUID deliveryPersonId) {
        return r2dbcRepository.findByDeliveryPersonId(deliveryPersonId);
    }

    @Override
    public Mono<FreelancerEligibility> save(FreelancerEligibility eligibility) {
        return r2dbcRepository.save(eligibility);
    }
}
