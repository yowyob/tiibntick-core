package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.EvaluationEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcEvaluationRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IEvaluationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EvaluationRepositoryAdapter implements IEvaluationRepository {

    private final R2dbcEvaluationRepository r2dbc;

    @Override public Mono<EvaluationEntity> save(EvaluationEntity e)                                                     { return r2dbc.save(e); }
    @Override public Mono<EvaluationEntity> findById(UUID id)                                                             { return r2dbc.findById(id); }
    @Override public Flux<EvaluationEntity> findByDeliveryId(UUID deliveryId)                                             { return r2dbc.findByDeliveryId(deliveryId); }
    @Override public Flux<EvaluationEntity> findByEvaluatedActorId(UUID evaluatedActorId)                                 { return r2dbc.findByEvaluatedActorId(evaluatedActorId); }
    @Override public Mono<EvaluationEntity> findByDeliveryIdAndEvaluationType(UUID deliveryId, String evaluationType)     { return r2dbc.findByDeliveryIdAndEvaluationType(deliveryId, evaluationType); }
}
