package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for GofpUser persistence operations.
 */
public interface GofpUserRepository {

    Mono<GofpUser> save(GofpUser user);

    Mono<GofpUser> findById(UUID id);

    Mono<GofpUser> findByCoreUserId(UUID coreUserId);

    Mono<GofpUser> findByEmail(String email);

    Flux<GofpUser> findAll();

    Mono<Void> deleteById(UUID id);
}
