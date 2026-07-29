package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges IDeliveryNeedRepository to the R2DBC Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class DeliveryNeedRepositoryAdapter implements IDeliveryNeedRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.DeliveryNeedRepository r2dbcRepository;

    @Override
    public Mono<DeliveryNeed> save(DeliveryNeed deliveryNeed) {
        return r2dbcRepository.save(deliveryNeed);
    }

    @Override
    public Mono<DeliveryNeed> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Flux<DeliveryNeed> findAllByUserId(UUID userId) {
        return r2dbcRepository.findAllByUserId(userId);
    }

    @Override
    public Flux<DeliveryNeed> findAllByStatus(DeliveryNeedStatus status) {
        return r2dbcRepository.findAllByStatus(status);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }
}
