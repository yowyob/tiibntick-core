package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryPersonAvailabilityEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcDeliveryPersonAvailabilityRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IDeliveryPersonAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeliveryPersonAvailabilityRepositoryAdapter implements IDeliveryPersonAvailabilityRepository {

    private final R2dbcDeliveryPersonAvailabilityRepository r2dbc;

    @Override public Mono<DeliveryPersonAvailabilityEntity> save(DeliveryPersonAvailabilityEntity e)              { return r2dbc.save(e); }
    @Override public Mono<DeliveryPersonAvailabilityEntity> findByFreelancerActorId(UUID freelancerActorId)       { return r2dbc.findByFreelancerActorId(freelancerActorId); }
    @Override public Flux<DeliveryPersonAvailabilityEntity> findAllAvailable()                                    { return r2dbc.findAllAvailable(); }
}
