package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryNeedEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IDeliveryNeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeliveryNeedRepositoryAdapter implements IDeliveryNeedRepository {

    private final R2dbcDeliveryNeedRepository r2dbc;

    @Override public Mono<DeliveryNeedEntity> save(DeliveryNeedEntity e)              { return r2dbc.save(e); }
    @Override public Mono<DeliveryNeedEntity> findById(UUID id)                        { return r2dbc.findById(id); }
    @Override public Flux<DeliveryNeedEntity> findByClientActorId(UUID clientActorId)  { return r2dbc.findByClientActorId(clientActorId); }
    @Override public Flux<DeliveryNeedEntity> findByStatus(String status)              { return r2dbc.findByStatus(status); }
}
