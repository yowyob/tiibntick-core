package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerVehicle;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface FreelancerVehicleRepository extends ReactiveCrudRepository<FreelancerVehicle, UUID> {
    Mono<FreelancerVehicle> findByFreelancerId(UUID freelancerId);
}
