package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.EarnBadgeCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IEarnBadgeUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.BadgeAnchorPayload;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IBadgeAnchorPort;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.event.BadgeEarnedEvent;
import com.yowyob.tiibntick.core.actor.domain.model.Badge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Slf4j
@Service
public class ActorBadgeService implements IEarnBadgeUseCase {

    private final IDelivererRepository delivererRepository;
    private final IFreelancerRepository freelancerRepository;
    private final IRelayOperatorRepository relayOperatorRepository;
    private final IActorEventPublisher eventPublisher;
    private final IBadgeAnchorPort badgeAnchorPort;

    public ActorBadgeService(IDelivererRepository delivererRepository,
                              IFreelancerRepository freelancerRepository,
                              IRelayOperatorRepository relayOperatorRepository,
                              IActorEventPublisher eventPublisher,
                              IBadgeAnchorPort badgeAnchorPort) {
        this.delivererRepository = delivererRepository;
        this.freelancerRepository = freelancerRepository;
        this.relayOperatorRepository = relayOperatorRepository;
        this.eventPublisher = eventPublisher;
        this.badgeAnchorPort = badgeAnchorPort;
    }

    @Override
    public Mono<Void> earnBadge(EarnBadgeCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        return anchorBadge(command)
                .map(txHash -> Badge.earn(command.badgeCode(), command.badgeLabel()).withBlockchainProof(txHash))
                .defaultIfEmpty(Badge.earn(command.badgeCode(), command.badgeLabel()))
                .flatMap(badge -> saveAndPublish(command, badge));
    }

    private Mono<Void> saveAndPublish(EarnBadgeCommand command, Badge badge) {
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

    /**
     * Anchors the badge on the blockchain via {@code tnt-trust-core}, best-effort.
     * A trust-anchoring failure must never fail badge earning — the badge is then
     * persisted without a blockchain proof.
     */
    private Mono<String> anchorBadge(EarnBadgeCommand command) {
        BadgeAnchorPayload payload = new BadgeAnchorPayload(
                command.tenantId(), command.actorId(), command.badgeCode(), command.badgeLabel());
        return badgeAnchorPort.anchor(payload)
                .onErrorResume(e -> {
                    log.warn("Failed to anchor badge on-chain — actorId={}, badgeCode={}: {}",
                            command.actorId(), command.badgeCode(), e.getMessage());
                    return Mono.empty();
                });
    }
}
