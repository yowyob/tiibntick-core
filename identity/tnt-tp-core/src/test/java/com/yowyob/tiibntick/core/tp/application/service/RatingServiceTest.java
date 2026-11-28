package com.yowyob.tiibntick.core.tp.application.service;

import com.yowyob.tiibntick.core.tp.application.port.in.command.RateThirdPartyCommand;
import com.yowyob.tiibntick.core.tp.application.port.out.ThirdPartyRatingRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntClientProfileRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntTpEventPublisher;
import com.yowyob.tiibntick.core.tp.domain.model.ThirdPartyRating;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A rated party without a tp-core client profile (delivery person, relay operator, Go client) keeps its rating. */
class RatingServiceTest {

    private final ThirdPartyRatingRepository ratings = mock(ThirdPartyRatingRepository.class);
    private final TntClientProfileRepository profiles = mock(TntClientProfileRepository.class);
    private final TntTpEventPublisher events = mock(TntTpEventPublisher.class);
    private final RatingService service = new RatingService(ratings, profiles, events);

    @Test
    void ratingIsSavedWithContextAndKindEvenWithoutTpProfile() {
        UUID tenant = UUID.randomUUID(), rated = UUID.randomUUID(), rater = UUID.randomUUID();
        when(ratings.existsByDeliveryIdAndRaterActorIdAndSubjectKind("D-1", rater, "DELIVERY_PERSON"))
                .thenReturn(Mono.just(false));
        when(ratings.save(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(profiles.findByThirdPartyId(tenant, rated)).thenReturn(Mono.empty());
        when(events.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.rate(new RateThirdPartyCommand(tenant, rated, rater, "D-1", 4.0, "ok",
                        "CLIENT_TO_DP", "DELIVERY_PERSON")))
                .expectNextMatches(r -> r.getScore() == 4.0 && "CLIENT_TO_DP".equals(r.getRatingContext())
                        && "DELIVERY_PERSON".equals(r.getSubjectKind()) && rated.equals(r.getRatedThirdPartyId()))
                .verifyComplete();
        verify(profiles, never()).save(any());
    }

    @Test
    void aSecondRatingOfTheSameMissionByTheSameRaterIsRefused() {
        UUID rater = UUID.randomUUID();
        when(ratings.existsByDeliveryIdAndRaterActorIdAndSubjectKind("D-2", rater, null)).thenReturn(Mono.just(true));
        StepVerifier.create(service.rate(new RateThirdPartyCommand(UUID.randomUUID(), UUID.randomUUID(), rater,
                        "D-2", 5.0, null)))
                .expectError(IllegalStateException.class)
                .verify();
        verify(ratings, never()).save(any(ThirdPartyRating.class));
    }

    /** Lot L3 (C3): the client rates the courier and the relay operator of the same delivery. */
    @Test
    void theSameRaterMayRateAnotherKindOfPartyOfTheSameMission() {
        UUID tenant = UUID.randomUUID(), courier = UUID.randomUUID(), operator = UUID.randomUUID();
        UUID rater = UUID.randomUUID();
        when(ratings.existsByDeliveryIdAndRaterActorIdAndSubjectKind("D-3", rater, "DELIVERY_PERSON"))
                .thenReturn(Mono.just(true));
        when(ratings.existsByDeliveryIdAndRaterActorIdAndSubjectKind("D-3", rater, "RELAY_OPERATOR"))
                .thenReturn(Mono.just(false));
        when(ratings.save(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(profiles.findByThirdPartyId(tenant, operator)).thenReturn(Mono.empty());
        when(events.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.rate(new RateThirdPartyCommand(tenant, operator, rater, "D-3", 5.0, null,
                        "CLIENT_TO_RP", "RELAY_OPERATOR")))
                .expectNextMatches(r -> "RELAY_OPERATOR".equals(r.getSubjectKind()))
                .verifyComplete();
        StepVerifier.create(service.rate(new RateThirdPartyCommand(tenant, courier, rater, "D-3", 5.0, null,
                        "CLIENT_TO_DP", "DELIVERY_PERSON")))
                .expectError(IllegalStateException.class)
                .verify();
    }
}
