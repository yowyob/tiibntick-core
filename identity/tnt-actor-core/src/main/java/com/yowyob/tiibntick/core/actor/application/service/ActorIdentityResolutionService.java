package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IUpdateActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.UpdateActorIdentityCommand;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.domain.model.ActorIdentitySummary;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelUserAccountPort;
import com.yowyob.tiibntick.core.actor.domain.model.KernelUserAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class ActorIdentityResolutionService implements IResolveActorIdentityUseCase, IUpdateActorIdentityUseCase {

    private final IKernelActorPort kernelActorPort;
    private final IKernelUserAccountPort userAccountPort;

    public ActorIdentityResolutionService(IKernelActorPort kernelActorPort) {
        this(kernelActorPort, null);
    }

    @Autowired
    public ActorIdentityResolutionService(IKernelActorPort kernelActorPort,
                                          @Nullable IKernelUserAccountPort userAccountPort) {
        this.kernelActorPort = kernelActorPort;
        this.userAccountPort = userAccountPort;
    }

    @Override
    public Mono<ActorIdentitySummary> resolve(UUID actorId) {
        return kernelActorPort.findById(actorId).map(ActorIdentitySummary::from);
    }

    @Override
    public Mono<ActorIdentitySummary> resolveCurrent() {
        // The business actor has no e-mail, names or photo: complete it with the Kernel user account, best effort.
        return kernelActorPort.findCurrent().map(ActorIdentitySummary::from)
                .flatMap(identity -> account().map(identity::withAccount).defaultIfEmpty(identity));
    }

    private Mono<KernelUserAccount> account() {
        if (userAccountPort == null) {
            return Mono.empty();
        }
        return userAccountPort.findCurrent().onErrorResume(e -> Mono.empty());
    }

    @Override
    public Mono<ActorIdentitySummary> updateCurrent(UpdateActorIdentityCommand command) {
        return kernelActorPort.updateCurrentIdentity(command).map(ActorIdentitySummary::from);
    }
}
