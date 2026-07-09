package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence;

import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.mapper.PlatformClientPersistenceMapper;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IApiKeyRepository;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKey;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Implements {@link IApiKeyRepository}.
 *
 * @author MANFOUO Braun
 */
public class ApiKeyRepositoryAdapter implements IApiKeyRepository {

    private final ApiKeyR2dbcRepository repository;

    public ApiKeyRepositoryAdapter(ApiKeyR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<ApiKey> save(ApiKey apiKey) {
        return repository.save(PlatformClientPersistenceMapper.toEntity(apiKey))
                .map(PlatformClientPersistenceMapper::toDomain);
    }

    @Override
    public Mono<ApiKey> findById(UUID id) {
        return repository.findById(id.toString())
                .map(PlatformClientPersistenceMapper::toDomain);
    }

    @Override
    public Flux<ApiKey> findAllByPlatformClientId(UUID platformClientId) {
        return repository.findByPlatformClientId(platformClientId.toString())
                .map(PlatformClientPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> markLastUsed(UUID id, Instant lastUsedAt) {
        return repository.findById(id.toString())
                .flatMap(entity -> {
                    entity.setLastUsedAt(lastUsedAt);
                    return repository.save(entity);
                })
                .then();
    }
}
