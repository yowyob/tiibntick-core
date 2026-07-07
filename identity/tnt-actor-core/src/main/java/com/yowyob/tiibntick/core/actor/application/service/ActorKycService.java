package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.SubmitKycCommand;
import com.yowyob.tiibntick.core.actor.application.command.ValidateKycCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.ISubmitKycUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IValidateKycUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.event.KycValidatedEvent;
import com.yowyob.tiibntick.core.actor.domain.exception.KycAlreadyVerifiedException;
import com.yowyob.tiibntick.core.actor.domain.model.KycStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Service
public class ActorKycService implements ISubmitKycUseCase, IValidateKycUseCase {

    private final IDelivererRepository delivererRepository;
    private final IFreelancerRepository freelancerRepository;
    private final IRelayOperatorRepository relayOperatorRepository;
    private final IActorEventPublisher eventPublisher;

    public ActorKycService(IDelivererRepository delivererRepository,
                      IFreelancerRepository freelancerRepository,
                      IRelayOperatorRepository relayOperatorRepository,
                      IActorEventPublisher eventPublisher) {
        this.delivererRepository = delivererRepository;
        this.freelancerRepository = freelancerRepository;
        this.relayOperatorRepository = relayOperatorRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<Void> submitKyc(SubmitKycCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return switch (command.actorType()) {
            case PERMANENT_DELIVERER -> delivererRepository
                    .findByActorId(command.tenantId(), command.actorId())
                    .flatMap(p -> {
                        if (p.isKycVerified()) {
                            return Mono.error(new KycAlreadyVerifiedException(p.actorId()));
                        }
                        return delivererRepository.save(p.withKycStatus(KycStatus.UNDER_REVIEW));
                    })
                    .then();
            case FREELANCER -> freelancerRepository
                    .findByActorId(command.tenantId(), command.actorId())
                    .flatMap(p -> {
                        if (p.isKycVerified()) {
                            return Mono.error(new KycAlreadyVerifiedException(p.actorId()));
                        }
                        return freelancerRepository.save(p.withKycStatus(KycStatus.UNDER_REVIEW));
                    })
                    .then();
            case RELAY_OPERATOR -> relayOperatorRepository
                    .findByActorId(command.tenantId(), command.actorId())
                    .flatMap(p -> {
                        if (p.isKycVerified()) {
                            return Mono.error(new KycAlreadyVerifiedException(p.actorId()));
                        }
                        return relayOperatorRepository.save(p.withKycStatus(KycStatus.UNDER_REVIEW));
                    })
                    .then();
            default -> Mono.error(new IllegalArgumentException(
                    "KYC not applicable for actor type: " + command.actorType()));
        };
    }

    @Override
    public Mono<Void> validateKyc(ValidateKycCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        KycValidatedEvent event = KycValidatedEvent.of(command.actorId(), command.tenantId(),
                command.actorType().name(), command.newKycStatus().name(), command.validatedBy());

        Mono<Void> saveMono = switch (command.actorType()) {
            case PERMANENT_DELIVERER -> delivererRepository
                    .findByActorId(command.tenantId(), command.actorId())
                    .flatMap(p -> delivererRepository.save(p.withKycStatus(command.newKycStatus())))
                    .then();
            case FREELANCER -> freelancerRepository
                    .findByActorId(command.tenantId(), command.actorId())
                    .flatMap(p -> freelancerRepository.save(p.withKycStatus(command.newKycStatus())))
                    .then();
            case RELAY_OPERATOR -> relayOperatorRepository
                    .findByActorId(command.tenantId(), command.actorId())
                    .flatMap(p -> relayOperatorRepository.save(p.withKycStatus(command.newKycStatus())))
                    .then();
            default -> Mono.error(new IllegalArgumentException(
                    "KYC validation not applicable for actor type: " + command.actorType()));
        };

        return saveMono.then(eventPublisher.publishKycValidated(event));
    }
}
