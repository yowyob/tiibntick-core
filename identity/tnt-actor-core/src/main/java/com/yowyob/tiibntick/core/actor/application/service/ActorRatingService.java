package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.RateActorCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IRateActorUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Application service for actor rating operations.
 *
 * <p> — Added {@code @RequirePermission} guard from tnt-roles-core.
 * The {@code actor:write} permission is required to rate any actor type,
 * enforced via the {@code TntPermissionAspect} AOP interceptor.
 *
 * @author MANFOUO Braun
 */
@Service
public class ActorRatingService implements IRateActorUseCase {

    private final IDelivererRepository delivererRepository;
    private final IFreelancerRepository freelancerRepository;
    private final IRelayOperatorRepository relayOperatorRepository;

    public ActorRatingService(IDelivererRepository delivererRepository,
                               IFreelancerRepository freelancerRepository,
                               IRelayOperatorRepository relayOperatorRepository) {
        this.delivererRepository = delivererRepository;
        this.freelancerRepository = freelancerRepository;
        this.relayOperatorRepository = relayOperatorRepository;
    }

    /**
     * Rates an actor of any supported type.
     *
     * <p>Requires {@code actor:write} permission (via {@code @RequirePermission}).
     * Clients, deliverers, and freelancers all have this permission in their default role
     * definitions ({@code TntRole}).
     *
     * @param command the rating command carrying actorId, actorType and score
     * @return empty Mono on success
     */
    @Override
    @RequirePermission(resource = "actor", action = "write")
    public Mono<Void> rateActor(RateActorCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return switch (command.actorType()) {
            case PERMANENT_DELIVERER -> rateDeliverer(command);
            case FREELANCER          -> rateFreelancer(command);
            case RELAY_OPERATOR      -> rateRelayOperator(command);
            default -> Mono.error(new IllegalArgumentException(
                    "Rating not supported for actor type: " + command.actorType()));
        };
    }

    private Mono<Void> rateDeliverer(RateActorCommand command) {
        return delivererRepository.findByActorId(command.tenantId(), command.actorId())
                .flatMap(profile -> delivererRepository.save(
                        profile.withRating(profile.rating().addRating(command.score()))))
                .then();
    }

    private Mono<Void> rateFreelancer(RateActorCommand command) {
        return freelancerRepository.findByActorId(command.tenantId(), command.actorId())
                .flatMap(profile -> freelancerRepository.save(
                        profile.withRating(profile.rating().addRating(command.score()))))
                .then();
    }

    private Mono<Void> rateRelayOperator(RateActorCommand command) {
        return relayOperatorRepository.findByActorId(command.tenantId(), command.actorId())
                .flatMap(profile -> relayOperatorRepository.save(
                        profile.withRating(profile.rating().addRating(command.score()))))
                .then();
    }
}
