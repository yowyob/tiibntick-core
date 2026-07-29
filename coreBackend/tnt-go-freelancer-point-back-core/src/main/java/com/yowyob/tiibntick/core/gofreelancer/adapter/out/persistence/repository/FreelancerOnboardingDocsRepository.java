package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerOnboardingDocs;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface FreelancerOnboardingDocsRepository extends ReactiveCrudRepository<FreelancerOnboardingDocs, UUID> {
    Mono<FreelancerOnboardingDocs> findByFreelancerId(UUID freelancerId);
}
