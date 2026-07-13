package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.SubscriptionEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcSubscriptionRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.ISubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionRepositoryAdapter implements ISubscriptionRepository {

    private final R2dbcSubscriptionRepository r2dbc;

    @Override public Mono<SubscriptionEntity> save(SubscriptionEntity e)                      { return r2dbc.save(e); }
    @Override public Mono<SubscriptionEntity> findById(UUID id)                                { return r2dbc.findById(id); }
    @Override public Mono<SubscriptionEntity> findByFreelancerActorId(UUID freelancerActorId)  { return r2dbc.findByFreelancerActorId(freelancerActorId); }
}
