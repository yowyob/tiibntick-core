package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.CreateFreelancerProfileCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateFreelancerProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindFreelancerUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.domain.event.ActorStatusChangedEvent;
import com.yowyob.tiibntick.core.actor.domain.exception.FreelancerNotFoundException;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.actor.domain.model.ServiceZoneId;
import com.yowyob.tiibntick.core.roles.application.port.in.AssignTntRoleUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.yowyob.tiibntick.core.actor.application.port.out.IProfileWelcomePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FreelancerService implements ICreateFreelancerProfileUseCase, IFindFreelancerUseCase {

    private ProfileCreationHooks creationHooks = ProfileCreationHooks.none();

    /** Welcome notice and e-mail once a profile is really created; optional so hand-built services still work. */
    @Autowired(required = false)
    public void setCreationHooks(ProfileCreationHooks creationHooks) {
        this.creationHooks = creationHooks;
    }

    private static final Logger log = LoggerFactory.getLogger(FreelancerService.class);

    private final IFreelancerRepository freelancerRepository;
    private final IActorEventPublisher eventPublisher;
    private final IKernelActorPort kernelActorPort;
    private final AssignTntRoleUseCase assignRoleUseCase;

    public FreelancerService(IFreelancerRepository freelancerRepository,
                              IActorEventPublisher eventPublisher,
                              IKernelActorPort kernelActorPort,
                              AssignTntRoleUseCase assignRoleUseCase) {
        this.freelancerRepository = freelancerRepository;
        this.eventPublisher = eventPublisher;
        this.kernelActorPort = kernelActorPort;
        this.assignRoleUseCase = assignRoleUseCase;
    }

    @Override
    @Transactional
    public Mono<FreelancerProfile> createFreelancerProfile(CreateFreelancerProfileCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return freelancerRepository.existsByActorId(command.tenantId(), command.actorId())
                .flatMap(exists -> {
                    Mono<FreelancerProfile> profileMono;
                    if (exists) {
                        profileMono = freelancerRepository.findByActorId(command.tenantId(), command.actorId());
                    } else {
                        // A profile belongs to the person: one created from another space is returned, never duplicated.
                        profileMono = freelancerRepository.findFirstByActorId(command.actorId())
                                .switchIfEmpty(Mono.defer(() -> newProfile(command)));
                    }
                    return profileMono.flatMap(profile -> grantFreelancerRole(profile, command.tenantId(), command.actorId()));
                });
    }

    private Mono<FreelancerProfile> newProfile(CreateFreelancerProfileCommand command) {
        return kernelActorPort.exists(command.actorId())
                .doOnNext(found -> {
                    if (!found) {
                        log.warn("Kernel actor {} not found or unreachable — " +
                                "creating freelancer profile without Kernel validation", command.actorId());
                    }
                })
                .then(Mono.defer(() -> {
                    FreelancerProfile profile = FreelancerProfile.create(
                            command.tenantId(), command.actorId(),
                            command.serviceZoneIds(), command.availabilitySlots());
                    return freelancerRepository.save(profile)
                            .flatMap(saved -> eventPublisher.publishActorStatusChanged(
                                    ActorStatusChangedEvent.of(saved.actorId(), saved.tenantId(),
                                            null, saved.actorStatus().name(), "freelancer_profile_created"))
                                    .thenReturn(saved))
                            .flatMap(created -> creationHooks.profileCreated(
                                    IProfileWelcomePort.Kind.FREELANCER, created.tenantId(), created.actorId())
                                    .thenReturn(created));
                }));
    }

    @Override
    public Mono<FreelancerProfile> findMine(UUID tenantId, UUID actorId) {
        return freelancerRepository.findByActorId(tenantId, actorId)
                .switchIfEmpty(Mono.defer(() -> freelancerRepository.findFirstByActorId(actorId)));
    }

    private Mono<FreelancerProfile> grantFreelancerRole(FreelancerProfile profile, UUID tenantId, UUID userId) {
        return assignRoleUseCase.assignRole(tenantId, userId, "FREELANCER", tenantId)
                .thenReturn(profile)
                .onErrorResume(err -> {
                    log.warn("FREELANCER role assignment failed for actor {} in tenant {} (best-effort — profile returned): {}",
                            userId, tenantId, err.getMessage());
                    return Mono.just(profile);
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
