package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.CreateRelayOperatorProfileCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateRelayOperatorProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.event.ActorStatusChangedEvent;
import com.yowyob.tiibntick.core.actor.domain.model.RelayOperatorProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.yowyob.tiibntick.core.actor.application.port.out.IProfileWelcomePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelayOperatorService implements ICreateRelayOperatorProfileUseCase {

    private ProfileCreationHooks creationHooks = ProfileCreationHooks.none();

    /** Welcome notice and e-mail once a profile is really created; optional so hand-built services still work. */
    @Autowired(required = false)
    public void setCreationHooks(ProfileCreationHooks creationHooks) {
        this.creationHooks = creationHooks;
    }

    private static final Logger log = LoggerFactory.getLogger(RelayOperatorService.class);

    private final IRelayOperatorRepository relayOperatorRepository;
    private final IActorEventPublisher eventPublisher;
    private final IKernelActorPort kernelActorPort;

    public RelayOperatorService(IRelayOperatorRepository relayOperatorRepository,
                                 IActorEventPublisher eventPublisher,
                                 IKernelActorPort kernelActorPort) {
        this.relayOperatorRepository = relayOperatorRepository;
        this.eventPublisher = eventPublisher;
        this.kernelActorPort = kernelActorPort;
    }

    @Override
    @Transactional
    public Mono<RelayOperatorProfile> createRelayOperatorProfile(CreateRelayOperatorProfileCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return relayOperatorRepository.existsByActorId(command.tenantId(), command.actorId())
                .flatMap(exists -> {
                    if (exists) {
                        return relayOperatorRepository.findByActorId(command.tenantId(), command.actorId());
                    }
                    // A profile belongs to the person: one created from another space is returned, never duplicated.
                    return relayOperatorRepository.findFirstByActorId(command.actorId())
                            .switchIfEmpty(Mono.defer(() -> newProfile(command)));
                });
    }

    private Mono<RelayOperatorProfile> newProfile(CreateRelayOperatorProfileCommand command) {
        return kernelActorPort.exists(command.actorId())
                .doOnNext(found -> {
                    if (!found) {
                        log.warn("Kernel actor {} not found or unreachable — " +
                                "creating relay operator profile without Kernel validation", command.actorId());
                    }
                })
                .then(Mono.defer(() -> {
                    RelayOperatorProfile profile = RelayOperatorProfile.create(
                            command.tenantId(), command.actorId(),
                            command.hubId(), command.openingHours(), command.declaredCapacityParcels());
                    return relayOperatorRepository.save(profile)
                            .flatMap(saved -> eventPublisher.publishActorStatusChanged(
                                    ActorStatusChangedEvent.of(saved.actorId(), saved.tenantId(),
                                            null, saved.actorStatus().name(), "relay_operator_profile_created"))
                                    .thenReturn(saved))
                            .flatMap(created -> creationHooks.profileCreated(
                                    IProfileWelcomePort.Kind.RELAY_OPERATOR, created.tenantId(), created.actorId())
                                    .thenReturn(created));
                }));
    }

    public Mono<RelayOperatorProfile> findByActorId(java.util.UUID tenantId, java.util.UUID actorId) {
        return relayOperatorRepository.findByActorId(tenantId, actorId);
    }

    public Mono<RelayOperatorProfile> findByHubId(java.util.UUID tenantId, java.util.UUID hubId) {
        return relayOperatorRepository.findByHubId(tenantId, hubId);
    }
}
