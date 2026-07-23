package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.UpdateActorLocationCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IGetAvailableDeliverersNearUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IUpdateActorLocationUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.event.ActorLocationUpdatedEvent;
import com.yowyob.tiibntick.core.actor.domain.model.ActorLocation;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DelivererLocationService implements IUpdateActorLocationUseCase, IGetAvailableDeliverersNearUseCase {

    private final IDelivererRepository delivererRepository;
    private final IFreelancerRepository freelancerRepository;
    private final IActorEventPublisher eventPublisher;

    public DelivererLocationService(IDelivererRepository delivererRepository,
                                     IFreelancerRepository freelancerRepository,
                                     IActorEventPublisher eventPublisher) {
        this.delivererRepository = delivererRepository;
        this.freelancerRepository = freelancerRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Mono<Void> updateLocation(UpdateActorLocationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        ActorLocation newLocation = ActorLocation.of(
                command.latitude(), command.longitude(), command.accuracy(),
                java.time.Instant.now(), command.source());

        Mono<Void> updateMono;
        if (command.actorType() == ActorType.FREELANCER) {
            updateMono = freelancerRepository.findByActorId(command.tenantId(), command.actorId())
                    .flatMap(profile -> freelancerRepository.save(profile.withLocation(newLocation)))
                    .then();
        } else {
            updateMono = delivererRepository.findByActorId(command.tenantId(), command.actorId())
                    .flatMap(profile -> delivererRepository.save(profile.withLocation(newLocation)))
                    .then();
        }

        ActorLocationUpdatedEvent event = ActorLocationUpdatedEvent.of(
                command.actorId(), command.tenantId(), command.actorType().name(),
                command.latitude(), command.longitude(), command.accuracy(), command.source().name());

        return updateMono
                .then(eventPublisher.publishLocationUpdated(event));
    }

    @Override
    public Flux<DelivererProfile> findAvailableNear(UUID tenantId, double latitude, double longitude,
                                                     double radiusKm, double minCapacityKg) {
        return delivererRepository.findAvailableNear(tenantId, latitude, longitude, radiusKm, minCapacityKg);
    }
}
