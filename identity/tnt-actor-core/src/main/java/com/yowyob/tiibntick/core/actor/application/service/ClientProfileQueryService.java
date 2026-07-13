package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IFindClientUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IClientProfileRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class ClientProfileQueryService implements IFindClientUseCase {

    private final IClientProfileRepository repository;

    public ClientProfileQueryService(IClientProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<ClientProfile> findByActorId(UUID tenantId, UUID actorId) {
        return repository.findByActorId(tenantId, actorId);
    }
}
