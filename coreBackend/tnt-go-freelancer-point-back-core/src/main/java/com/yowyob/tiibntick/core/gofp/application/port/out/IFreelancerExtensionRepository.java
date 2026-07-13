package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.FreelancerExtensionEntity;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IFreelancerExtensionRepository {
    Mono<FreelancerExtensionEntity> save(FreelancerExtensionEntity entity);
    Mono<FreelancerExtensionEntity> findByFreelancerActorId(UUID freelancerActorId);
}
