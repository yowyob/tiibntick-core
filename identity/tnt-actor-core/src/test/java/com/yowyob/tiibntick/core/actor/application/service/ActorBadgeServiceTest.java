package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.EarnBadgeCommand;
import com.yowyob.tiibntick.core.actor.application.port.out.BadgeAnchorPayload;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IBadgeAnchorPort;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.event.BadgeEarnedEvent;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ActorBadgeService}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActorBadgeService")
class ActorBadgeServiceTest {

    @Mock
    private IDelivererRepository delivererRepository;
    @Mock
    private IFreelancerRepository freelancerRepository;
    @Mock
    private IRelayOperatorRepository relayOperatorRepository;
    @Mock
    private IActorEventPublisher eventPublisher;
    @Mock
    private IBadgeAnchorPort badgeAnchorPort;

    private ActorBadgeService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private ActorBadgeService service() {
        return new ActorBadgeService(delivererRepository, freelancerRepository,
                relayOperatorRepository, eventPublisher, badgeAnchorPort);
    }

    private DelivererProfile sampleDeliverer() {
        return DelivererProfile.create(TENANT_ID, ACTOR_ID, UUID.randomUUID(), UUID.randomUUID(),
                500.0, DelivererType.PERMANENT);
    }

    @Test
    @DisplayName("earnBadge saves the badge with a blockchain proof when anchoring succeeds")
    void earnBadgeAttachesProofWhenAnchoringSucceeds() {
        service = service();
        DelivererProfile profile = sampleDeliverer();

        when(badgeAnchorPort.anchor(any())).thenReturn(Mono.just("tx-hash-001"));
        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(profile));
        when(delivererRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishBadgeEarned(any(BadgeEarnedEvent.class))).thenReturn(Mono.empty());

        EarnBadgeCommand cmd = new EarnBadgeCommand(
                TENANT_ID, ACTOR_ID, ActorType.PERMANENT_DELIVERER, "100_DELIVERIES", "100 Deliveries");

        StepVerifier.create(service.earnBadge(cmd)).verifyComplete();

        ArgumentCaptor<DelivererProfile> captor = ArgumentCaptor.forClass(DelivererProfile.class);
        verify(delivererRepository).save(captor.capture());
        assertThat(captor.getValue().badges()).anyMatch(b ->
                b.code().equals("100_DELIVERIES") && b.blockchainTxHash().equals("tx-hash-001"));
    }

    @Test
    @DisplayName("earnBadge still saves the badge (without proof) when anchoring fails")
    void earnBadgeSucceedsWhenAnchoringFails() {
        service = service();
        DelivererProfile profile = sampleDeliverer();

        when(badgeAnchorPort.anchor(any())).thenReturn(Mono.error(new RuntimeException("trust unavailable")));
        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(profile));
        when(delivererRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishBadgeEarned(any(BadgeEarnedEvent.class))).thenReturn(Mono.empty());

        EarnBadgeCommand cmd = new EarnBadgeCommand(
                TENANT_ID, ACTOR_ID, ActorType.PERMANENT_DELIVERER, "100_DELIVERIES", "100 Deliveries");

        StepVerifier.create(service.earnBadge(cmd)).verifyComplete();

        ArgumentCaptor<DelivererProfile> captor = ArgumentCaptor.forClass(DelivererProfile.class);
        verify(delivererRepository).save(captor.capture());
        assertThat(captor.getValue().badges()).anyMatch(b ->
                b.code().equals("100_DELIVERIES") && !b.hasBlockchainProof());
    }

    @Test
    @DisplayName("earnBadge sends the actor and badge identifiers to the anchor port")
    void earnBadgeSendsCorrectPayload() {
        service = service();
        DelivererProfile profile = sampleDeliverer();

        when(badgeAnchorPort.anchor(any())).thenReturn(Mono.just("tx-hash-001"));
        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(profile));
        when(delivererRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishBadgeEarned(any(BadgeEarnedEvent.class))).thenReturn(Mono.empty());

        EarnBadgeCommand cmd = new EarnBadgeCommand(
                TENANT_ID, ACTOR_ID, ActorType.PERMANENT_DELIVERER, "100_DELIVERIES", "100 Deliveries");

        StepVerifier.create(service.earnBadge(cmd)).verifyComplete();

        ArgumentCaptor<BadgeAnchorPayload> captor = ArgumentCaptor.forClass(BadgeAnchorPayload.class);
        verify(badgeAnchorPort).anchor(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR_ID);
        assertThat(captor.getValue().badgeCode()).isEqualTo("100_DELIVERIES");
    }
}
