package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerEligibilityUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPriceQuoteUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.MatchingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L2 — le statut, la livraison et le livreur d'un besoin sont dérivés de son annonce core : un
 * besoin jamais publié (sans annonce) ne peut plus être « assigné » en écrivant le besoin. L'ancien
 * chemin direct répond 409 et n'écrit rien (architecture-cible 3.4 (g) : un besoin sans annonce
 * reste PENDING).
 */
@ExtendWith(MockitoExtension.class)
class DeliveryNeedAssignFreelancerTest {

    private static final UUID NEED_ID       = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID       = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FREELANCER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PICKUP_ID     = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID DROPOFF_ID    = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Mock private IDeliveryNeedRepository deliveryNeedRepository;
    @Mock private AdminRelayPointUseCase adminRelayPointUseCase;
    @Mock private PushNotificationPort pushNotificationPort;
    @Mock private MatchingUseCase matchingUseCase;
    @Mock private AddressUseCase addressUseCase;
    @Mock private GofpUserRepository gofpUserRepository;
    @Mock private GofpFreelancerRepository gofpFreelancerRepository;
    @Mock private FreelancerPriceQuoteUseCase freelancerPriceQuoteUseCase;
    @Mock private FreelancerEligibilityUseCase freelancerEligibilityUseCase;

    private DeliveryNeedApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryNeedApplicationService(
                deliveryNeedRepository, adminRelayPointUseCase, pushNotificationPort,
                matchingUseCase, addressUseCase, gofpUserRepository,
                gofpFreelancerRepository, freelancerPriceQuoteUseCase, freelancerEligibilityUseCase);
    }

    @Test
    void assignFreelancer_unpublishedNeed_isRefusedAndWritesNothing() {
        DeliveryNeed pendingNeed = DeliveryNeed.builder()
                .id(NEED_ID)
                .userId(USER_ID)
                .pickupAddressId(PICKUP_ID)
                .deliveryAddressId(DROPOFF_ID)
                .title("Colis Douala → Yaoundé")
                .createdAt(Instant.now())
                .build();

        GofpFreelancer approvedFreelancer = GofpFreelancer.builder()
                .id(FREELANCER_ID)
                .status(FreelancerStatus.APPROVED)
                .isActive(true)
                .build();

        when(deliveryNeedRepository.findById(NEED_ID)).thenReturn(Mono.just(pendingNeed));
        when(gofpFreelancerRepository.findById(FREELANCER_ID)).thenReturn(Mono.just(approvedFreelancer));

        // Lot C-52: a client may only elect a courier who applied; the staff path is exercised here.
        Mono<DeliveryNeedResponseDTO> result = service.assignFreelancer(NEED_ID, FREELANCER_ID, USER_ID, true);

        StepVerifier.create(result)
                .expectErrorMatches(e -> e instanceof org.springframework.web.server.ResponseStatusException r
                        && r.getStatusCode().value() == 409)
                .verify();

        verify(deliveryNeedRepository, org.mockito.Mockito.never()).save(any());
    }
}
