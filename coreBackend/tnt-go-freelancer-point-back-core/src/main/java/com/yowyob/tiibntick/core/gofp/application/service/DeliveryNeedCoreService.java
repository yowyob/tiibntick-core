package com.yowyob.tiibntick.core.gofp.application.service;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryNeedEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IDeliveryNeedUseCase;
import com.yowyob.tiibntick.core.gofp.application.port.out.IDeliveryNeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryNeedCoreService implements IDeliveryNeedUseCase {

    private final IDeliveryNeedRepository deliveryNeedRepository;

    @Override
    public Mono<DeliveryNeedEntity> createDeliveryNeed(DeliveryNeedEntity need) {
        need.setId(UUID.randomUUID());
        need.setStatus("OPEN");
        need.setCreatedAt(Instant.now());
        need.setUpdatedAt(Instant.now());
        return deliveryNeedRepository.save(need);
    }

    @Override
    public Mono<DeliveryNeedEntity> assignDelivery(UUID needId, UUID deliveryId) {
        return deliveryNeedRepository.findById(needId)
            .flatMap(need -> {
                need.setDeliveryId(deliveryId);
                need.setStatus("ASSIGNED");
                need.setUpdatedAt(Instant.now());
                return deliveryNeedRepository.save(need);
            });
    }

    @Override
    public Mono<DeliveryNeedEntity> cancel(UUID needId) {
        return deliveryNeedRepository.findById(needId)
            .flatMap(need -> {
                need.setStatus("CANCELLED");
                need.setUpdatedAt(Instant.now());
                return deliveryNeedRepository.save(need);
            });
    }

    @Override public Mono<DeliveryNeedEntity> findById(UUID id)                    { return deliveryNeedRepository.findById(id); }
    @Override public Flux<DeliveryNeedEntity> findByClientActorId(UUID clientActorId) { return deliveryNeedRepository.findByClientActorId(clientActorId); }
    @Override public Flux<DeliveryNeedEntity> findOpen()                             { return deliveryNeedRepository.findByStatus("OPEN"); }
}
