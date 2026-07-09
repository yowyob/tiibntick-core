package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence;

import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.mapper.PlatformClientPersistenceMapper;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IApiKeyRotationRepository;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKeyRotationRecord;
import reactor.core.publisher.Mono;

/**
 * Implements {@link IApiKeyRotationRepository}.
 *
 * @author MANFOUO Braun
 */
public class ApiKeyRotationRepositoryAdapter implements IApiKeyRotationRepository {

    private final ApiKeyRotationHistoryR2dbcRepository repository;

    public ApiKeyRotationRepositoryAdapter(ApiKeyRotationHistoryR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<ApiKeyRotationRecord> save(ApiKeyRotationRecord record) {
        return repository.save(PlatformClientPersistenceMapper.toEntity(record))
                .map(PlatformClientPersistenceMapper::toDomain);
    }
}
