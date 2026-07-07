package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.AssignMissionCommand;
import com.yowyob.tiibntick.core.actor.application.command.ReleaseMissionCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IAssignMissionToDelivererUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IReleaseMissionFromDelivererUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.domain.event.DelivererMissionAssignedEvent;
import com.yowyob.tiibntick.core.actor.domain.exception.DelivererNotFoundException;
import com.yowyob.tiibntick.core.actor.domain.exception.ActorNotAvailableException;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Service
public class DelivererAvailabilityService
        implements IAssignMissionToDelivererUseCase, IReleaseMissionFromDelivererUseCase {

    private final IDelivererRepository delivererRepository;
    private final IActorEventPublisher eventPublisher;

    public DelivererAvailabilityService(IDelivererRepository delivererRepository,
                                        IActorEventPublisher eventPublisher) {
        this.delivererRepository = delivererRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<DelivererProfile> assignMission(AssignMissionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return delivererRepository.findByActorId(command.tenantId(), command.delivererActorId())
                .switchIfEmpty(Mono.error(new DelivererNotFoundException(command.tenantId(), command.delivererActorId())))
                .flatMap(profile -> {
                    if (!profile.isAvailableForMission()) {
                        return Mono.error(new ActorNotAvailableException(profile.actorId(),
                                profile.actorStatus().name()));
                    }
                    DelivererProfile updated = profile.assignMission(command.missionId());
                    return delivererRepository.save(updated)
                            .flatMap(saved -> {
                                DelivererMissionAssignedEvent event = DelivererMissionAssignedEvent.of(
                                        saved.actorId(), saved.tenantId(),
                                        saved.agencyId(), saved.branchId(), command.missionId());
                                return eventPublisher.publishMissionAssigned(event).thenReturn(saved);
                            });
                });
    }

    @Override
    public Mono<DelivererProfile> releaseMission(ReleaseMissionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return delivererRepository.findByActorId(command.tenantId(), command.delivererActorId())
                .switchIfEmpty(Mono.error(new DelivererNotFoundException(command.tenantId(), command.delivererActorId())))
                .flatMap(profile -> {
                    DelivererProfile released = profile.releaseMission();
                    return delivererRepository.save(released);
                });
    }
}
