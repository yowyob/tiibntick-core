package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpClient;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.client.ClientStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for GofpClient persistence operations.
 */
public interface GofpClientRepository {

    Mono<GofpClient> save(GofpClient client);

    Mono<GofpClient> findById(UUID id);

    Mono<GofpClient> findByCoreClientId(UUID coreClientId);

    Mono<GofpClient> findByCoreUserId(UUID coreUserId);

    Flux<GofpClient> findAllByStatus(ClientStatus status);

    Flux<GofpClient> findAll();

    Mono<Void> deleteById(UUID id);
}
