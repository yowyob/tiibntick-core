package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.SubscriptionEntity;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ISubscriptionRepository {
    Mono<SubscriptionEntity> save(SubscriptionEntity entity);
    Mono<SubscriptionEntity> findById(UUID id);
    Mono<SubscriptionEntity> findByFreelancerActorId(UUID freelancerActorId);
}
