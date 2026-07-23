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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelayOperatorService implements ICreateRelayOperatorProfileUseCase {

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
                                                .thenReturn(saved));
                            }));
                });
    }

    public Mono<RelayOperatorProfile> findByActorId(java.util.UUID tenantId, java.util.UUID actorId) {
        return relayOperatorRepository.findByActorId(tenantId, actorId);
    }

    public Mono<RelayOperatorProfile> findByHubId(java.util.UUID tenantId, java.util.UUID hubId) {
        return relayOperatorRepository.findByHubId(tenantId, hubId);
    }
}
