package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPricingPolicyUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.MatchingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/**
 * Prouve que la garde d'ownership sur les besoins de livraison <em>mord</em>.
 *
 * <h3>Invariants testés</h3>
 * <ul>
 *   <li><strong>non-propriétaire authentifié → 403</strong> : un appelant dont le {@code userId}
 *       diffère du {@code userId} du besoin reçoit un {@link AccessDeniedException} sur
 *       {@code getCandidatesWithPricing}.</li>
 *   <li><strong>propriétaire authentifié → passe la garde</strong> : l'appelant propriétaire
 *       atteint le matching sans être bloqué.</li>
 *   <li><strong>callerId=null → AccessDeniedException (lot 25.1)</strong> : un appelant
 *       authentifié dont le {@code sub} n'est pas un UUID valide (jeton client plateforme,
 *       clé d'API passerelle) obtient {@code userId=null} ; la garde refuse plutôt que de
 *       laisser passer silencieusement. Le fail-open précédent ({@code return null}) a été
 *       fermé par le lot 25.1.</li>
 * </ul>
 *
 * <h3>Note technique sur ownershipGuardEnabled</h3>
 * {@code DeliveryNeedApplicationService#ownershipGuardEnabled} est initialisé à {@code true}
 * directement dans le champ ({@code = true}). En test unitaire sans contexte Spring, ce
 * champ n'est pas remplacé par {@code @Value} : la garde est donc <em>active par défaut</em>,
 * ce qui est le comportement souhaité.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryNeedOwnershipGuardTest {

    private static final UUID NEED_ID      = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_ID     = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID NON_OWNER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PICKUP_ID    = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID DROPOFF_ID   = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Mock private IDeliveryNeedRepository deliveryNeedRepository;
    @Mock private AdminRelayPointUseCase adminRelayPointUseCase;
    @Mock private PushNotificationPort pushNotificationPort;
    @Mock private MatchingUseCase matchingUseCase;
    @Mock private AddressUseCase addressUseCase;
    @Mock private GofpUserRepository gofpUserRepository;
    @Mock private GofpFreelancerRepository gofpFreelancerRepository;
    @Mock private FreelancerPricingPolicyUseCase freelancerPricingPolicyUseCase;
    @Mock private DatabaseClient databaseClient;

    private DeliveryNeedApplicationService service;

    private DeliveryNeed needOwnedByOwner;

    @BeforeEach
    void setUp() {
        service = new DeliveryNeedApplicationService(
                deliveryNeedRepository, adminRelayPointUseCase, pushNotificationPort,
                matchingUseCase, addressUseCase, gofpUserRepository,
                gofpFreelancerRepository, freelancerPricingPolicyUseCase, databaseClient);

        needOwnedByOwner = DeliveryNeed.builder()
                .id(NEED_ID)
                .userId(OWNER_ID)
                .pickupAddressId(PICKUP_ID)
                .deliveryAddressId(DROPOFF_ID)
                .title("Besoin de test")
                .status(DeliveryNeedStatus.PENDING)
                .build();
    }

    /**
     * La garde MORD : un non-propriétaire reçoit AccessDeniedException (→ HTTP 403).
     * Le check RBAC précède le matching : aucun appel à addressUseCase ni matchingUseCase.
     */
    @Test
    void getCandidatesWithPricing_nonOwner_throwsAccessDeniedException() {
        when(deliveryNeedRepository.findById(NEED_ID)).thenReturn(Mono.just(needOwnedByOwner));

        StepVerifier.create(
                service.getCandidatesWithPricing(NEED_ID, NON_OWNER_ID)
        )
        .expectErrorSatisfies(err -> {
            assertThat(err).isInstanceOf(AccessDeniedException.class);
            assertThat(err.getMessage()).contains("belongs to another user");
        })
        .verify();
    }

    /**
     * Le propriétaire passe la garde et atteint le matching.
     */
    @Test
    void getCandidatesWithPricing_owner_passesGuardAndReachesMatching() {
        AddressDTO addr = AddressDTO.builder()
                .id(PICKUP_ID)
                .address(Address.builder()
                        .landmark("Test")
                        .city("Yaoundé")
                        .country("Cameroun")
                        .coordinates(GeoCoordinates.of(3.866, 11.517))
                        .build())
                .build();

        TopsisRankingUseCase.FreelancerCandidate candidate =
                TopsisRankingUseCase.FreelancerCandidate.builder()
                        .freelancerId(UUID.randomUUID())
                        .latitude(3.866).longitude(11.517)
                        .rating(4.0).topsisScore(0.7)
                        .build();

        when(deliveryNeedRepository.findById(NEED_ID)).thenReturn(Mono.just(needOwnedByOwner));
        when(addressUseCase.getAddressById(any())).thenReturn(Mono.just(addr));
        when(matchingUseCase.processMatchingForDeliveryNeed(
                any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(Mono.just(List.of(candidate)));
        when(gofpFreelancerRepository.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(gofpFreelancerRepository.findByCoreFreelancerId(any(UUID.class))).thenReturn(Mono.empty());
        when(freelancerPricingPolicyUseCase.getPolicy(any())).thenReturn(Mono.empty());
        when(gofpUserRepository.findByCoreUserId(any())).thenReturn(Mono.empty());

        StepVerifier.create(
                service.getCandidatesWithPricing(NEED_ID, OWNER_ID).collectList()
        )
        .assertNext(dtos -> assertThat(dtos).isNotEmpty())
        .verifyComplete();
    }

    /**
     * callerId=null → AccessDeniedException (lot 25.1).
     * Un appelant authentifié dont le sub n'est pas un UUID obtient userId=null.
     * La garde refuse explicitement plutôt que de laisser passer silencieusement.
     */
    @Test
    void getCandidatesWithPricing_nullCaller_throwsAccessDeniedException() {
        when(deliveryNeedRepository.findById(NEED_ID)).thenReturn(Mono.just(needOwnedByOwner));

        StepVerifier.create(
                service.getCandidatesWithPricing(NEED_ID, null)
        )
        .expectErrorSatisfies(err -> {
            assertThat(err).isInstanceOf(AccessDeniedException.class);
            assertThat(err.getMessage()).contains("authenticated identity");
        })
        .verify();
    }
}
