package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.CreateFreelancerProfileCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateFreelancerProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindFreelancerUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.event.ActorStatusChangedEvent;
import com.yowyob.tiibntick.core.actor.domain.exception.FreelancerNotFoundException;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.actor.domain.model.ServiceZoneId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@Service
public class FreelancerService implements ICreateFreelancerProfileUseCase, IFindFreelancerUseCase {

    private final IFreelancerRepository freelancerRepository;
    private final IActorEventPublisher eventPublisher;

    public FreelancerService(IFreelancerRepository freelancerRepository,
                              IActorEventPublisher eventPublisher) {
        this.freelancerRepository = freelancerRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<FreelancerProfile> createFreelancerProfile(CreateFreelancerProfileCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return freelancerRepository.existsByActorId(command.tenantId(), command.actorId())
                .flatMap(exists -> {
                    if (exists) {
                        return freelancerRepository.findByActorId(command.tenantId(), command.actorId());
                    }
                    FreelancerProfile profile = FreelancerProfile.create(
                            command.tenantId(), command.actorId(),
                            command.serviceZoneIds(), command.availabilitySlots());
                    return freelancerRepository.save(profile)
                            .flatMap(saved -> eventPublisher.publishActorStatusChanged(
                                    ActorStatusChangedEvent.of(saved.actorId(), saved.tenantId(),
                                            null, saved.actorStatus().name(), "freelancer_profile_created"))
                                    .thenReturn(saved));
                });
    }

    @Override
    public Mono<FreelancerProfile> findByActorId(UUID tenantId, UUID actorId) {
        return freelancerRepository.findByActorId(tenantId, actorId)
                .switchIfEmpty(Mono.error(new FreelancerNotFoundException(tenantId, actorId)));
    }

    @Override
    public Flux<FreelancerProfile> findAvailableInZone(UUID tenantId, ServiceZoneId serviceZoneId) {
        return freelancerRepository.findActiveByServiceZone(tenantId, serviceZoneId.value());
    }

    @Override
    public Flux<FreelancerProfile> findByAssociatedAgency(UUID tenantId, UUID agencyId) {
        return freelancerRepository.findByAssociatedAgency(tenantId, agencyId);
    }
}
