package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.EarnBadgeCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IEarnBadgeUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.event.BadgeEarnedEvent;
import com.yowyob.tiibntick.core.actor.domain.model.Badge;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Service
public class ActorBadgeService implements IEarnBadgeUseCase {

    private final IDelivererRepository delivererRepository;
    private final IFreelancerRepository freelancerRepository;
    private final IRelayOperatorRepository relayOperatorRepository;
    private final IActorEventPublisher eventPublisher;

    public ActorBadgeService(IDelivererRepository delivererRepository,
                              IFreelancerRepository freelancerRepository,
                              IRelayOperatorRepository relayOperatorRepository,
                              IActorEventPublisher eventPublisher) {
        this.delivererRepository = delivererRepository;
        this.freelancerRepository = freelancerRepository;
        this.relayOperatorRepository = relayOperatorRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<Void> earnBadge(EarnBadgeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Badge badge = Badge.earn(command.badgeCode(), command.badgeLabel());
        BadgeEarnedEvent event = BadgeEarnedEvent.of(command.actorId(), command.tenantId(),
                command.actorType().name(), command.badgeCode(), command.badgeLabel());

        Mono<Void> saveMono = switch (command.actorType()) {
            case PERMANENT_DELIVERER -> delivererRepository
                    .findByActorId(command.tenantId(), command.actorId())
                    .flatMap(p -> delivererRepository.save(p.withBadge(badge)))
                    .then();
            case FREELANCER -> freelancerRepository
                    .findByActorId(command.tenantId(), command.actorId())
                    .flatMap(p -> freelancerRepository.save(p.withBadge(badge)))
                    .then();
            case RELAY_OPERATOR -> relayOperatorRepository
                    .findByActorId(command.tenantId(), command.actorId())
                    .flatMap(p -> relayOperatorRepository.save(p.withBadge(badge)))
                    .then();
            default -> Mono.error(new IllegalArgumentException(
                    "Badge not supported for actor type: " + command.actorType()));
        };

        return saveMono.then(eventPublisher.publishBadgeEarned(event));
    }
}
