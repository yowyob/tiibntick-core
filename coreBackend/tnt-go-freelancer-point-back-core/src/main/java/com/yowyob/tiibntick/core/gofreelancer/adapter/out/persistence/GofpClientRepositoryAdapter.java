package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.GofpClientR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpClient;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.client.ClientStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges the domain GofpClientRepository port to the R2DBC repository.
 */
@Component
@RequiredArgsConstructor
public class GofpClientRepositoryAdapter implements GofpClientRepository {

    private final GofpClientR2dbcRepository r2dbcRepository;

    @Override public Mono<GofpClient> save(GofpClient client) { return r2dbcRepository.save(client); }

    @Override public Mono<GofpClient> findById(UUID id) { return r2dbcRepository.findById(id); }

    @Override public Mono<GofpClient> findByCoreClientId(UUID coreClientId) { return r2dbcRepository.findByCoreClientId(coreClientId); }

    @Override public Mono<GofpClient> findByCoreUserId(UUID coreUserId) { return r2dbcRepository.findByCoreUserId(coreUserId); }

    @Override public Flux<GofpClient> findAllByStatus(ClientStatus status) { return r2dbcRepository.findAllByStatus(status); }

    @Override public Flux<GofpClient> findAll() { return r2dbcRepository.findAll(); }

    @Override public Mono<Void> deleteById(UUID id) { return r2dbcRepository.deleteById(id); }
}
