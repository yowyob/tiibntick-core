package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IUpdateActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.UpdateActorIdentityCommand;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.domain.model.ActorIdentitySummary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class ActorIdentityResolutionService implements IResolveActorIdentityUseCase, IUpdateActorIdentityUseCase {

    private final IKernelActorPort kernelActorPort;

    public ActorIdentityResolutionService(IKernelActorPort kernelActorPort) {
        this.kernelActorPort = kernelActorPort;
    }

    @Override
    public Mono<ActorIdentitySummary> resolve(UUID actorId) {
        return kernelActorPort.findById(actorId).map(ActorIdentitySummary::from);
    }

    @Override
    public Mono<ActorIdentitySummary> resolveCurrent() {
        return kernelActorPort.findCurrent().map(ActorIdentitySummary::from);
    }

    @Override
    public Mono<ActorIdentitySummary> updateCurrent(UpdateActorIdentityCommand command) {
        return kernelActorPort.updateCurrentIdentity(command).map(ActorIdentitySummary::from);
    }
}
