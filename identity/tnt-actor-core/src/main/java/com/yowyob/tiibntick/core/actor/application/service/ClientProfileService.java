package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.CreateClientProfileCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateClientProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IClientProfileRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.domain.event.ActorProfileUpdatedEvent;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@Service
public class ClientProfileService implements ICreateClientProfileUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClientProfileService.class);

    private final IClientProfileRepository clientProfileRepository;
    private final IKernelActorPort kernelActorPort;
    private final IActorEventPublisher eventPublisher;

    public ClientProfileService(IClientProfileRepository clientProfileRepository,
                                 IKernelActorPort kernelActorPort,
                                 IActorEventPublisher eventPublisher) {
        this.clientProfileRepository = clientProfileRepository;
        this.kernelActorPort = kernelActorPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<ClientProfile> createClientProfile(CreateClientProfileCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return clientProfileRepository.existsByActorId(command.tenantId(), command.actorId())
                .flatMap(exists -> {
                    if (exists) {
                        return clientProfileRepository.findByActorId(command.tenantId(), command.actorId());
                    }
                    return kernelActorPort.exists(command.actorId())
                            .doOnNext(found -> {
                                if (!found) {
                                    log.warn("Kernel actor {} not found or unreachable — " +
                                            "creating client profile without Kernel validation", command.actorId());
                                }
                            })
                            .then(Mono.defer(() -> {
                                ClientProfile profile = ClientProfile.create(command.tenantId(), command.actorId());
                                return clientProfileRepository.save(profile);
                            }));
                });
    }

    public Mono<ClientProfile> findByActorId(UUID tenantId, UUID actorId) {
        return clientProfileRepository.findByActorId(tenantId, actorId);
    }

    public Mono<ClientProfile> addLoyaltyPoints(UUID tenantId, UUID actorId, int points) {
        return clientProfileRepository.findByActorId(tenantId, actorId)
                .flatMap(profile -> clientProfileRepository.save(profile.addLoyaltyPoints(points)))
                .flatMap(saved -> eventPublisher.publishProfileUpdated(ActorProfileUpdatedEvent.of(
                                actorId, tenantId, "CLIENT", "LOYALTY_POINTS_ADDED"))
                        .thenReturn(saved));
    }
}
