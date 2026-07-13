package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.FreelancerExtensionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface R2dbcFreelancerExtensionRepository
        extends ReactiveCrudRepository<FreelancerExtensionEntity, UUID> {

    Mono<FreelancerExtensionEntity> findByFreelancerActorId(UUID freelancerActorId);
}
