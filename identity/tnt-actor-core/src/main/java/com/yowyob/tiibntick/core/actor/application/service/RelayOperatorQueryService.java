package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IFindRelayOperatorUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.model.RelayOperatorProfile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class RelayOperatorQueryService implements IFindRelayOperatorUseCase {

    private final IRelayOperatorRepository repository;

    public RelayOperatorQueryService(IRelayOperatorRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<RelayOperatorProfile> findByActorId(UUID tenantId, UUID actorId) {
        return repository.findByActorId(tenantId, actorId);
    }
}
