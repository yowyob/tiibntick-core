package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.user.UserStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for GofpUser operations.
 */
public interface GofpUserUseCase {

    Mono<GofpUser> createOrUpdate(GofpUser user);

    Mono<GofpUser> findById(UUID id);

    Mono<GofpUser> findByCoreUserId(UUID coreUserId);

    Mono<GofpUser> findByEmail(String email);

    Flux<GofpUser> findAll();

    Mono<GofpUser> updateStatus(UUID id, UserStatus status);

    Mono<Void> delete(UUID id);
}
