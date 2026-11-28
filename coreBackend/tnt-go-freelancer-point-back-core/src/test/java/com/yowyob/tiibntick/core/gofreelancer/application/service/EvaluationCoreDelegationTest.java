package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.command.RateActorCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IRateActorUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IClientProfileRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.EvaluationDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.SentimentAnalysisUseCase;
import com.yowyob.tiibntick.core.tp.application.port.in.command.RateThirdPartyCommand;
import com.yowyob.tiibntick.core.tp.application.service.RatingService;
import com.yowyob.tiibntick.core.tp.domain.model.ThirdPartyRating;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lot L3: an evaluation is a tp-core rating (one per mission and rater) plus the actor-core average,
 * addressed with core actor ids. Go keeps only the sentiment-based score.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationCoreDelegationTest {

    @Mock private SentimentAnalysisUseCase sentiment;
    @Mock private RatingService ratingService;
    @Mock private IRateActorUseCase rateActor;
    @Mock private IFreelancerRepository freelancerProfiles;
    @Mock private IClientProfileRepository clientProfiles;
    @Mock private GofpRelayPointRepository relayPoints;
    @Mock private TenantContextHolder tenantContextHolder;

    private EvaluationApplicationService service;

    private static final UUID TENANT = UUID.randomUUID();
    private final UUID freelancerActor = UUID.randomUUID();
    private final UUID clientActor = UUID.randomUUID();
    private FreelancerProfile freelancerProfile;
    private ClientProfile clientProfile;

    @BeforeEach
    void setUp() {
        service = new EvaluationApplicationService(sentiment, ratingService, rateActor, freelancerProfiles,
                clientProfiles, relayPoints, tenantContextHolder);
        lenient().when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        freelancerProfile = FreelancerProfile.create(TENANT, freelancerActor, List.of(), List.of());
        clientProfile = ClientProfile.create(TENANT, clientActor);
        lenient().when(freelancerProfiles.findById(eq(TENANT), any())).thenReturn(Mono.empty());
        lenient().when(clientProfiles.findById(eq(TENANT), any())).thenReturn(Mono.empty());
        lenient().when(relayPoints.findByCoreRelayPointId(any())).thenReturn(Mono.empty());
        lenient().when(relayPoints.findById(any(UUID.class))).thenReturn(Mono.empty());
        lenient().when(clientProfiles.findByActorId(eq(TENANT), any())).thenReturn(Mono.empty());
        lenient().when(freelancerProfiles.findByActorId(eq(TENANT), any())).thenReturn(Mono.empty());
        lenient().when(freelancerProfiles.findById(TENANT, freelancerProfile.id())).thenReturn(Mono.just(freelancerProfile));
        lenient().when(clientProfiles.findByActorId(TENANT, clientActor)).thenReturn(Mono.just(clientProfile));
    }

    @Test
    void clientRatesFreelancer_ratingAndAverageAreWrittenInTheCoreWithActorIds() {
        UUID delivery = UUID.randomUUID();
        EvaluationDTO dto = new EvaluationDTO();
        dto.setDeliveryId(delivery);
        dto.setEvaluatedId(freelancerProfile.id()); // a profile id is accepted
        dto.setEvaluatorId(clientActor);            // an actor id too
        dto.setRating(4);
        dto.setType("CLIENT_TO_DP");
        when(ratingService.rate(any())).thenAnswer(inv -> {
            RateThirdPartyCommand c = inv.getArgument(0);
            return Mono.just(ThirdPartyRating.create(c.tenantId(), c.ratedThirdPartyId(), c.raterActorId(),
                    c.deliveryId(), c.score(), c.comment(), c.ratingContext(), c.subjectKind()));
        });
        when(rateActor.rateActor(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.submitEvaluation(dto, asCaller(clientActor)))
                .assertNext(e -> {
                    assertThat(e.getEvaluatedId()).isEqualTo(freelancerActor);
                    assertThat(e.getEvaluatorId()).isEqualTo(clientActor);
                    assertThat(e.getDeliveryId()).isEqualTo(delivery);
                    assertThat(e.getRating()).isEqualTo(4);
                    assertThat(e.getType()).isEqualTo("CLIENT_TO_DP");
                    assertThat(e.getSubjectKind()).isEqualTo("DELIVERY_PERSON");
                })
                .verifyComplete();

        ArgumentCaptor<RateThirdPartyCommand> rated = ArgumentCaptor.forClass(RateThirdPartyCommand.class);
        verify(ratingService).rate(rated.capture());
        assertThat(rated.getValue().deliveryId()).isEqualTo(delivery.toString());
        assertThat(rated.getValue().ratingContext()).isEqualTo("CLIENT_TO_DP");
        ArgumentCaptor<RateActorCommand> avg = ArgumentCaptor.forClass(RateActorCommand.class);
        verify(rateActor).rateActor(avg.capture());
        assertThat(avg.getValue().actorId()).isEqualTo(freelancerActor);
        assertThat(avg.getValue().actorType()).isEqualTo(ActorType.FREELANCER);
    }

    @Test
    void secondRatingOfTheSameMission_isRefusedByTheCoreAndNoAverageIsWritten() {
        EvaluationDTO dto = new EvaluationDTO();
        dto.setDeliveryId(UUID.randomUUID());
        dto.setEvaluatedId(freelancerProfile.id());
        dto.setEvaluatorId(clientActor);
        dto.setRating(5);
        when(ratingService.rate(any())).thenReturn(Mono.error(new IllegalStateException("already rated")));

        StepVerifier.create(service.submitEvaluation(dto, asCaller(clientActor))).expectError(IllegalStateException.class).verify();
        verifyNoInteractions(rateActor);
    }

    @Test
    void unknownParty_isABadRequest() {
        EvaluationDTO dto = new EvaluationDTO();
        dto.setDeliveryId(UUID.randomUUID());
        dto.setEvaluatedId(UUID.randomUUID());
        dto.setEvaluatorId(clientActor);
        dto.setRating(3);

        StepVerifier.create(service.submitEvaluation(dto, asCaller(clientActor))).expectError(IllegalArgumentException.class).verify();
        verifyNoInteractions(ratingService);
    }

    @Test
    void listForAPerson_readsTheCoreRatingsOfItsActor() {
        UUID mission = UUID.randomUUID();
        when(ratingService.listRatings(TENANT, clientActor)).thenReturn(Flux.just(
                ThirdPartyRating.reconstitute(UUID.randomUUID(), TENANT, clientActor, freelancerActor,
                        mission.toString(), 2.0, "late", Instant.now(), "DP_TO_CLIENT", "CLIENT")));

        StepVerifier.create(service.getEvaluationsForPerson(clientActor))
                .assertNext(e -> {
                    assertThat(e.getDeliveryId()).isEqualTo(mission);
                    assertThat(e.getRating()).isEqualTo(2);
                    assertThat(e.getType()).isEqualTo("DP_TO_CLIENT");
                })
                .verifyComplete();
    }

    /** One account, two profiles (Go client and freelancer): addressed by its actor id, the type picks the side. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "CLIENT_TO_DP, FREELANCER, DELIVERY_PERSON",
            "DP_TO_CLIENT, CLIENT, CLIENT",
            "CLIENT_RATING_DELIVERY_PERSON, FREELANCER, DELIVERY_PERSON",
            "DELIVERY_PERSON_RATING_CLIENT, CLIENT, CLIENT"
    })
    void actorWithBothProfiles_isRatedOnTheSideNamedByTheType(String type, ActorType expectedType, String expectedKind) {
        UUID both = UUID.randomUUID();
        lenient().when(clientProfiles.findByActorId(TENANT, both)).thenReturn(Mono.just(ClientProfile.create(TENANT, both)));
        lenient().when(freelancerProfiles.findByActorId(TENANT, both))
                .thenReturn(Mono.just(FreelancerProfile.create(TENANT, both, List.of(), List.of())));
        EvaluationDTO dto = new EvaluationDTO();
        dto.setDeliveryId(UUID.randomUUID());
        dto.setEvaluatedId(both);
        dto.setEvaluatorId(clientActor);
        dto.setRating(5);
        dto.setType(type);
        when(ratingService.rate(any())).thenAnswer(inv -> {
            RateThirdPartyCommand c = inv.getArgument(0);
            return Mono.just(ThirdPartyRating.create(c.tenantId(), c.ratedThirdPartyId(), c.raterActorId(),
                    c.deliveryId(), c.score(), c.comment(), c.ratingContext(), c.subjectKind()));
        });
        when(rateActor.rateActor(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.submitEvaluation(dto, asCaller(clientActor)))
                .assertNext(e -> assertThat(e.getSubjectKind()).isEqualTo(expectedKind))
                .verifyComplete();
        ArgumentCaptor<RateActorCommand> avg = ArgumentCaptor.forClass(RateActorCommand.class);
        verify(rateActor).rateActor(avg.capture());
        assertThat(avg.getValue().actorId()).isEqualTo(both);
        assertThat(avg.getValue().actorType()).isEqualTo(expectedType);
    }

    private static com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext asCaller(UUID actor, String... roles) {
        return com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext.builder()
                .userId(actor).tenantId(TENANT).roles(java.util.Set.of(roles)).authenticated(true).build();
    }

    private EvaluationDTO clientRatesFreelancer(UUID evaluatorId) {
        EvaluationDTO dto = new EvaluationDTO();
        dto.setDeliveryId(UUID.randomUUID());
        dto.setEvaluatedId(freelancerProfile.id());
        dto.setEvaluatorId(evaluatorId);
        dto.setRating(4);
        dto.setType("CLIENT_TO_DP");
        return dto;
    }

    private void ratingAccepted() {
        when(ratingService.rate(any())).thenAnswer(inv -> {
            RateThirdPartyCommand c = inv.getArgument(0);
            return Mono.just(ThirdPartyRating.create(c.tenantId(), c.ratedThirdPartyId(), c.raterActorId(),
                    c.deliveryId(), c.score(), c.comment(), c.ratingContext(), c.subjectKind()));
        });
        when(rateActor.rateActor(any())).thenReturn(Mono.empty());
    }

    /** Lot L3 (C5): an evaluation in someone else's name is refused, nothing is written. */
    @Test
    void evaluatingInSomeoneElsesName_isRefused() {
        UUID victim = UUID.randomUUID();
        when(clientProfiles.findByActorId(TENANT, victim)).thenReturn(Mono.just(ClientProfile.create(TENANT, victim)));

        StepVerifier.create(service.submitEvaluation(clientRatesFreelancer(victim), asCaller(clientActor)))
                .expectError(org.springframework.security.access.AccessDeniedException.class)
                .verify();
        verifyNoInteractions(ratingService, rateActor);
    }

    @Test
    void evaluatingWithAnUnknownEvaluatorId_isRefusedToo() {
        StepVerifier.create(service.submitEvaluation(clientRatesFreelancer(UUID.randomUUID()), asCaller(clientActor)))
                .expectError(org.springframework.security.access.AccessDeniedException.class)
                .verify();
        verifyNoInteractions(ratingService);
    }

    @Test
    void withoutEvaluatorId_theCallerIsTheEvaluator() {
        ratingAccepted();
        StepVerifier.create(service.submitEvaluation(clientRatesFreelancer(null), asCaller(clientActor)))
                .assertNext(e -> assertThat(e.getEvaluatorId()).isEqualTo(clientActor))
                .verifyComplete();
    }

    @Test
    void theCallersOwnProfileIdIsAccepted() {
        when(clientProfiles.findById(TENANT, clientProfile.id())).thenReturn(Mono.just(clientProfile));
        ratingAccepted();
        StepVerifier.create(service.submitEvaluation(clientRatesFreelancer(clientProfile.id()), asCaller(clientActor)))
                .assertNext(e -> assertThat(e.getEvaluatorId()).isEqualTo(clientActor))
                .verifyComplete();
    }

    @Test
    void staffMayRecordAnEvaluationOnBehalfOfAParty() {
        ratingAccepted();
        StepVerifier.create(service.submitEvaluation(clientRatesFreelancer(clientActor),
                        asCaller(UUID.randomUUID(), "TNT_ADMIN")))
                .assertNext(e -> assertThat(e.getEvaluatorId()).isEqualTo(clientActor))
                .verifyComplete();
    }

    @Test
    void anAnonymousCaller_isRefused() {
        StepVerifier.create(service.submitEvaluation(clientRatesFreelancer(clientActor), null))
                .expectError(org.springframework.security.access.AccessDeniedException.class)
                .verify();
        verifyNoInteractions(ratingService);
    }
}
