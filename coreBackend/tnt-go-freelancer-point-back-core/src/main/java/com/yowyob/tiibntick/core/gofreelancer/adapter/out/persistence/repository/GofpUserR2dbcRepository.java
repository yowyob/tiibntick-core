package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive R2DBC repository for GofpUser entity.
 */
public interface GofpUserR2dbcRepository extends ReactiveCrudRepository<GofpUser, UUID> {

    Mono<GofpUser> findByCoreUserId(UUID coreUserId);

    Mono<GofpUser> findByEmail(String email);
}
