package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IFindMyProfilesUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IClientProfileRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ProfileRef;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reads the profile tables directly (repositories, not the secured query services): which profiles an account
 * holds must not depend on the roles in the token.
 */
@Service
public class MyProfilesService implements IFindMyProfilesUseCase {

    private final IClientProfileRepository clients;
    private final IFreelancerRepository freelancers;
    private final IDelivererRepository deliverers;
    private final IRelayOperatorRepository relayOperators;

    public MyProfilesService(IClientProfileRepository clients, IFreelancerRepository freelancers,
                             IDelivererRepository deliverers, IRelayOperatorRepository relayOperators) {
        this.clients = clients;
        this.freelancers = freelancers;
        this.deliverers = deliverers;
        this.relayOperators = relayOperators;
    }

    @Override
    public Flux<ProfileRef> findAll(UUID tenantId, UUID actorId) {
        return Flux.concat(
                clients.findByActorId(tenantId, actorId)
                        .switchIfEmpty(Mono.defer(() -> clients.findFirstByActorId(actorId)))
                        .map(p -> new ProfileRef("CLIENT", p.id(), p.tenantId(), String.valueOf(p.actorStatus()))),
                freelancers.findByActorId(tenantId, actorId)
                        .switchIfEmpty(Mono.defer(() -> freelancers.findFirstByActorId(actorId)))
                        .map(p -> new ProfileRef("FREELANCER", p.id(), p.tenantId(), String.valueOf(p.actorStatus()))),
                deliverers.findByActorId(tenantId, actorId)
                        .switchIfEmpty(Mono.defer(() -> deliverers.findFirstByActorId(actorId)))
                        .map(p -> new ProfileRef("DELIVERER", p.id(), p.tenantId(), String.valueOf(p.actorStatus()))),
                relayOperators.findByActorId(tenantId, actorId)
                        .switchIfEmpty(Mono.defer(() -> relayOperators.findFirstByActorId(actorId)))
                        .map(p -> new ProfileRef("RELAY_OPERATOR", p.id(), p.tenantId(), String.valueOf(p.actorStatus()))));
    }
}
