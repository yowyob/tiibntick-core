package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.RateActorCommand;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererType;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActorRatingServiceTest {

    @Mock private IDelivererRepository delivererRepository;
    @Mock private IFreelancerRepository freelancerRepository;
    @Mock private IRelayOperatorRepository relayOperatorRepository;
    @Mock private IActorEventPublisher eventPublisher;

    private ActorRatingService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ActorRatingService(delivererRepository, freelancerRepository,
                relayOperatorRepository, eventPublisher);
        lenient().when(eventPublisher.publishProfileUpdated(any())).thenReturn(Mono.empty());
    }

    @Test
    void rateDeliverer_shouldUpdateRatingScore() {
        DelivererProfile profile = DelivererProfile.create(
                TENANT_ID, ACTOR_ID, UUID.randomUUID(), UUID.randomUUID(), 50.0, DelivererType.PERMANENT);

        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(profile));

        ArgumentCaptor<DelivererProfile> captor = ArgumentCaptor.forClass(DelivererProfile.class);
        when(delivererRepository.save(captor.capture())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.rateActor(new RateActorCommand(TENANT_ID, ACTOR_ID,
                ActorType.PERMANENT_DELIVERER, 4.0, UUID.randomUUID())))
                .verifyComplete();

        DelivererProfile saved = captor.getValue();
        assertThat(saved.rating().totalRatings()).isEqualTo(1);
        assertThat(saved.rating().score()).isEqualTo(4.0);
    }

    @Test
    void rateFreelancer_shouldUpdateRatingScore() {
        FreelancerProfile profile = FreelancerProfile.create(TENANT_ID, ACTOR_ID, List.of(), List.of());

        when(freelancerRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(profile));

        ArgumentCaptor<FreelancerProfile> captor = ArgumentCaptor.forClass(FreelancerProfile.class);
        when(freelancerRepository.save(captor.capture())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.rateActor(new RateActorCommand(TENANT_ID, ACTOR_ID,
                ActorType.FREELANCER, 5.0, UUID.randomUUID())))
                .verifyComplete();

        FreelancerProfile saved = captor.getValue();
        assertThat(saved.rating().totalRatings()).isEqualTo(1);
        assertThat(saved.rating().score()).isEqualTo(5.0);
    }

    @Test
    void rateWithUnsupportedActorType_shouldError() {
        StepVerifier.create(service.rateActor(new RateActorCommand(TENANT_ID, ACTOR_ID,
                ActorType.CLIENT, 3.0, UUID.randomUUID())))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
