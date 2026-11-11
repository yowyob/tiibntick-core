package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryProof;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DeliveryProofRepository extends ReactiveCrudRepository<DeliveryProof, UUID> {

    Flux<DeliveryProof> findByDeliveryIdOrderByCapturedAtAsc(UUID deliveryId);

    Mono<DeliveryProof> findByDeliveryIdAndFileId(UUID deliveryId, UUID fileId);
}
