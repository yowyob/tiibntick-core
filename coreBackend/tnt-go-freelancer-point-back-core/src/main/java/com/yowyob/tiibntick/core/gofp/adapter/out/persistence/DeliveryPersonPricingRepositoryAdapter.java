package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryPersonPricingEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcDeliveryPersonPricingRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IDeliveryPersonPricingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeliveryPersonPricingRepositoryAdapter implements IDeliveryPersonPricingRepository {

    private final R2dbcDeliveryPersonPricingRepository r2dbc;

    @Override public Mono<DeliveryPersonPricingEntity> save(DeliveryPersonPricingEntity e)             { return r2dbc.save(e); }
    @Override public Mono<DeliveryPersonPricingEntity> findByFreelancerActorId(UUID freelancerActorId) { return r2dbc.findByFreelancerActorId(freelancerActorId); }
}
