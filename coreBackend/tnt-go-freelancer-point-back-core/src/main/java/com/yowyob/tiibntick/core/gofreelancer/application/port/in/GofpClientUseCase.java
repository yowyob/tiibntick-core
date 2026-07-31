package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpClient;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.client.ClientStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.user.LoyaltyStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for GofpClient operations.
 */
public interface GofpClientUseCase {

    Mono<GofpClient> createOrUpdate(GofpClient client);

    Mono<GofpClient> findById(UUID id);

    Mono<GofpClient> findByCoreClientId(UUID coreClientId);

    Mono<GofpClient> findByCoreUserId(UUID coreUserId);

    Flux<GofpClient> findAll();

    Flux<GofpClient> findByStatus(ClientStatus status);

    Mono<GofpClient> updateStatus(UUID id, ClientStatus status);

    Mono<GofpClient> updateLoyaltyStatus(UUID id, LoyaltyStatus loyaltyStatus);

    /** Increments totalOrders and recomputes loyalty tier. */
    Mono<GofpClient> recordOrder(UUID coreClientId);

    Mono<Void> delete(UUID id);
}
