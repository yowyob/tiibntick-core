package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges the domain DeliveryRepository port to the R2DBC
 * Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class GofpDeliveryRepositoryAdapter implements DeliveryRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.DeliveryRepository r2dbcRepository;

    @Override
    public Mono<Delivery> save(Delivery delivery) {
        return r2dbcRepository.save(delivery);
    }

    @Override
    public Mono<Delivery> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Mono<Delivery> findByAnnouncementId(UUID announcementId) {
        return r2dbcRepository.findByAnnouncementId(announcementId);
    }

    @Override
    public Flux<Delivery> findAllByFreelancerId(UUID freelancerId) {
        return r2dbcRepository.findAllByFreelancerId(freelancerId);
    }

    @Override
    public Flux<Delivery> findAllByStatus(DeliveryStatus status) {
        return r2dbcRepository.findAllByStatus(status);
    }

    @Override
    public Mono<Delivery> findByDeliveryNeedId(UUID deliveryNeedId) {
        return r2dbcRepository.findByDeliveryNeedId(deliveryNeedId);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }

    @Override
    public Flux<Delivery> findActiveByFreelancerId(UUID freelancerId) {
        return r2dbcRepository.findActiveByFreelancerId(freelancerId);
    }

    @Override
    public Mono<Double> sumOccupiedVolumeM3ByFreelancerId(UUID freelancerId) {
        return r2dbcRepository.sumOccupiedVolumeM3ByFreelancerId(freelancerId)
                .defaultIfEmpty(0.0);
    }
}
