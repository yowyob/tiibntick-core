package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.CreateClientProfileCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateClientProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IClientProfileRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@Service
public class ClientProfileService implements ICreateClientProfileUseCase {

    private final IClientProfileRepository clientProfileRepository;

    public ClientProfileService(IClientProfileRepository clientProfileRepository) {
        this.clientProfileRepository = clientProfileRepository;
    }

    @Override
    public Mono<ClientProfile> createClientProfile(CreateClientProfileCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return clientProfileRepository.existsByActorId(command.tenantId(), command.actorId())
                .flatMap(exists -> {
                    if (exists) {
                        return clientProfileRepository.findByActorId(command.tenantId(), command.actorId());
                    }
                    ClientProfile profile = ClientProfile.create(command.tenantId(), command.actorId());
                    return clientProfileRepository.save(profile);
                });
    }

    public Mono<ClientProfile> findByActorId(UUID tenantId, UUID actorId) {
        return clientProfileRepository.findByActorId(tenantId, actorId);
    }

    public Mono<ClientProfile> addLoyaltyPoints(UUID tenantId, UUID actorId, int points) {
        return clientProfileRepository.findByActorId(tenantId, actorId)
                .flatMap(profile -> clientProfileRepository.save(profile.addLoyaltyPoints(points)));
    }
}
