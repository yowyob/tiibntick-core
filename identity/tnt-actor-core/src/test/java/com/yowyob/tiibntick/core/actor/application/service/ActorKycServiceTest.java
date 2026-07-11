package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.ValidateKycCommand;
import com.yowyob.tiibntick.core.actor.application.port.out.ActorDidAnchorPayload;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorDidAnchorPort;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.event.KycValidatedEvent;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererType;
import com.yowyob.tiibntick.core.actor.domain.model.KycStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ActorKycService}, focused on the blockchain DID
 * anchoring behavior added to {@code validateKyc()}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActorKycService")
class ActorKycServiceTest {

    @Mock
    private IDelivererRepository delivererRepository;
    @Mock
    private IFreelancerRepository freelancerRepository;
    @Mock
    private IRelayOperatorRepository relayOperatorRepository;
    @Mock
    private IActorEventPublisher eventPublisher;
    @Mock
    private IActorDidAnchorPort actorDidAnchorPort;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private ActorKycService service() {
        return new ActorKycService(delivererRepository, freelancerRepository,
                relayOperatorRepository, eventPublisher, actorDidAnchorPort);
    }

    private DelivererProfile sampleDeliverer() {
        return DelivererProfile.create(TENANT_ID, ACTOR_ID, UUID.randomUUID(), UUID.randomUUID(),
                500.0, DelivererType.PERMANENT);
    }

    private ValidateKycCommand verifyCommand() {
        return new ValidateKycCommand(TENANT_ID, ACTOR_ID, ActorType.PERMANENT_DELIVERER,
                KycStatus.VERIFIED, "admin-1", null);
    }

    @Test
    @DisplayName("validateKyc anchors a blockchain DID when transitioning to VERIFIED")
    void validateKycAnchorsDidOnVerification() {
        DelivererProfile profile = sampleDeliverer();

        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(profile));
        when(delivererRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(actorDidAnchorPort.issueDid(any())).thenReturn(Mono.just("did:tiibntick:" + TENANT_ID + ":" + ACTOR_ID));
        when(eventPublisher.publishKycValidated(any(KycValidatedEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(service().validateKyc(verifyCommand())).verifyComplete();

        ArgumentCaptor<DelivererProfile> captor = ArgumentCaptor.forClass(DelivererProfile.class);
        verify(delivererRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getValue().blockchainDid()).isEqualTo("did:tiibntick:" + TENANT_ID + ":" + ACTOR_ID);
    }

    @Test
    @DisplayName("validateKyc does not anchor a DID when the actor already has one")
    void validateKycSkipsAnchorWhenDidAlreadyPresent() {
        DelivererProfile profile = sampleDeliverer().withBlockchainDid("did:tiibntick:existing");

        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(profile));
        when(delivererRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishKycValidated(any(KycValidatedEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(service().validateKyc(verifyCommand())).verifyComplete();

        verify(actorDidAnchorPort, never()).issueDid(any());
    }

    @Test
    @DisplayName("validateKyc does not anchor a DID when the new status is not VERIFIED")
    void validateKycSkipsAnchorWhenNotVerified() {
        DelivererProfile profile = sampleDeliverer();
        ValidateKycCommand cmd = new ValidateKycCommand(TENANT_ID, ACTOR_ID,
                ActorType.PERMANENT_DELIVERER, KycStatus.REJECTED, "admin-1", "incomplete documents");

        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(profile));
        when(delivererRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishKycValidated(any(KycValidatedEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(service().validateKyc(cmd)).verifyComplete();

        verify(actorDidAnchorPort, never()).issueDid(any());
    }

    @Test
    @DisplayName("validateKyc still succeeds when DID anchoring fails (best-effort)")
    void validateKycSucceedsWhenAnchorFails() {
        DelivererProfile profile = sampleDeliverer();

        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(profile));
        when(delivererRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(actorDidAnchorPort.issueDid(any())).thenReturn(Mono.error(new RuntimeException("trust unavailable")));
        when(eventPublisher.publishKycValidated(any(KycValidatedEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(service().validateKyc(verifyCommand())).verifyComplete();

        verify(eventPublisher).publishKycValidated(any(KycValidatedEvent.class));
    }

    @Test
    @DisplayName("validateKyc sends the actor and tenant identifiers to the anchor port")
    void validateKycSendsCorrectPayload() {
        DelivererProfile profile = sampleDeliverer();

        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(profile));
        when(delivererRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(actorDidAnchorPort.issueDid(any())).thenReturn(Mono.just("did:tiibntick:x"));
        when(eventPublisher.publishKycValidated(any(KycValidatedEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(service().validateKyc(verifyCommand())).verifyComplete();

        ArgumentCaptor<ActorDidAnchorPayload> captor = ArgumentCaptor.forClass(ActorDidAnchorPayload.class);
        verify(actorDidAnchorPort).issueDid(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR_ID);
    }
}
