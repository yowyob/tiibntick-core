package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.AssociateFreelancerCommand;
import com.yowyob.tiibntick.core.actor.application.command.DissociateFreelancerCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IAssociateFreelancerUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IDissociateFreelancerUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.event.FreelancerAssociatedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.FreelancerDissociatedEvent;
import com.yowyob.tiibntick.core.actor.domain.exception.FreelancerNotFoundException;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FreelancerAssociationService
        implements IAssociateFreelancerUseCase, IDissociateFreelancerUseCase {

    private final IFreelancerRepository freelancerRepository;
    private final IActorEventPublisher eventPublisher;

    public FreelancerAssociationService(IFreelancerRepository freelancerRepository,
                                         IActorEventPublisher eventPublisher) {
        this.freelancerRepository = freelancerRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Mono<FreelancerProfile> associate(AssociateFreelancerCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return freelancerRepository.findByActorId(command.tenantId(), command.freelancerActorId())
                .switchIfEmpty(Mono.error(
                        new FreelancerNotFoundException(command.tenantId(), command.freelancerActorId())))
                .flatMap(profile -> {
                    FreelancerProfile updated = profile.associateWithAgency(command.agencyId());
                    return freelancerRepository.save(updated)
                            .flatMap(saved -> eventPublisher.publishFreelancerAssociated(
                                    FreelancerAssociatedEvent.of(saved.actorId(), saved.tenantId(),
                                            command.agencyId()))
                                    .thenReturn(saved));
                });
    }

    @Override
    @Transactional
    public Mono<FreelancerProfile> dissociate(DissociateFreelancerCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return freelancerRepository.findByActorId(command.tenantId(), command.freelancerActorId())
                .switchIfEmpty(Mono.error(
                        new FreelancerNotFoundException(command.tenantId(), command.freelancerActorId())))
                .flatMap(profile -> {
                    FreelancerProfile updated = profile.dissociateFromAgency(command.agencyId());
                    return freelancerRepository.save(updated)
                            .flatMap(saved -> eventPublisher.publishFreelancerDissociated(
                                    FreelancerDissociatedEvent.of(saved.actorId(), saved.tenantId(),
                                            command.agencyId(), command.reason()))
                                    .thenReturn(saved));
                });
    }
}
