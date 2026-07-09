package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence;

import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.mapper.PlatformClientPersistenceMapper;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IClientPermissionRepository;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientPermission;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implements {@link IClientPermissionRepository}.
 *
 * @author MANFOUO Braun
 */
public class ClientPermissionRepositoryAdapter implements IClientPermissionRepository {

    private final ClientPermissionR2dbcRepository repository;

    public ClientPermissionRepositoryAdapter(ClientPermissionR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<ClientPermission> save(ClientPermission permission) {
        return repository.save(PlatformClientPersistenceMapper.toEntity(permission))
                .map(PlatformClientPersistenceMapper::toDomain);
    }

    @Override
    public Flux<ClientPermission> findByPlatformClientId(UUID platformClientId) {
        return repository.findByPlatformClientId(platformClientId.toString())
                .map(PlatformClientPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> deleteByPlatformClientId(UUID platformClientId) {
        return repository.deleteByPlatformClientId(platformClientId.toString());
    }
}
