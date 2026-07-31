package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.GofpUserR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges the domain GofpUserRepository port to the R2DBC repository.
 */
@Component
@RequiredArgsConstructor
public class GofpUserRepositoryAdapter implements GofpUserRepository {

    private final GofpUserR2dbcRepository r2dbcRepository;

    @Override public Mono<GofpUser> save(GofpUser user) { return r2dbcRepository.save(user); }

    @Override public Mono<GofpUser> findById(UUID id) { return r2dbcRepository.findById(id); }

    @Override public Mono<GofpUser> findByCoreUserId(UUID coreUserId) { return r2dbcRepository.findByCoreUserId(coreUserId); }

    @Override public Mono<GofpUser> findByEmail(String email) { return r2dbcRepository.findByEmail(email); }

    @Override public Flux<GofpUser> findAll() { return r2dbcRepository.findAll(); }

    @Override public Mono<Void> deleteById(UUID id) { return r2dbcRepository.deleteById(id); }
}
