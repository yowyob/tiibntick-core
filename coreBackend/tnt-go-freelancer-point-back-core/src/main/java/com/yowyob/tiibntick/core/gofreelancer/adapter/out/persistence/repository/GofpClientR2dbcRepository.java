package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpClient;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.client.ClientStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive R2DBC repository for GofpClient entity.
 */
public interface GofpClientR2dbcRepository extends ReactiveCrudRepository<GofpClient, UUID> {

    Mono<GofpClient> findByCoreClientId(UUID coreClientId);

    Mono<GofpClient> findByCoreUserId(UUID coreUserId);

    Flux<GofpClient> findAllByStatus(ClientStatus status);
}
