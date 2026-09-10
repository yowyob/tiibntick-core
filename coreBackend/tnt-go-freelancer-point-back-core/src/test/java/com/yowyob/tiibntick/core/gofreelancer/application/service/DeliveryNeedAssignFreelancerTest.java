package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.MatchingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prouve que assignFreelancer() écrit dans assigned_freelancer_id (pas delivery_id),
 * laisse delivery_id à null et positionne le statut à ASSIGNED.
 *
 * Ce test ÉCHOUE sur github/main (where need.setDeliveryId(freelancerId) génère une violation FK)
 * et PASSE sur cette branche (need.setAssignedFreelancerId(freelancerId)).
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
    @Mock private DatabaseClient databaseClient;

    private DeliveryNeedApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryNeedApplicationService(
                deliveryNeedRepository, adminRelayPointUseCase, pushNotificationPort,
                matchingUseCase, addressUseCase, gofpUserRepository, databaseClient);
    }

    @Test
    void assignFreelancer_setsAssignedFreelancerIdNotDeliveryId() {
        DeliveryNeed pendingNeed = DeliveryNeed.builder()
                .id(NEED_ID)
                .userId(USER_ID)
                .pickupAddressId(PICKUP_ID)
                .deliveryAddressId(DROPOFF_ID)
                .title("Colis Douala → Yaoundé")
                .status(DeliveryNeedStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(deliveryNeedRepository.findById(NEED_ID)).thenReturn(Mono.just(pendingNeed));

        ArgumentCaptor<DeliveryNeed> savedCaptor = ArgumentCaptor.forClass(DeliveryNeed.class);
        when(deliveryNeedRepository.save(savedCaptor.capture()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(addressUseCase.getAddressById(any())).thenReturn(Mono.error(new RuntimeException("not found")));

        Mono<DeliveryNeedResponseDTO> result = service.assignFreelancer(NEED_ID, FREELANCER_ID);

        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);
                    assertThat(dto.getAssignedFreelancerId()).isEqualTo(FREELANCER_ID);
                    assertThat(dto.getDeliveryId()).isNull();
                })
                .verifyComplete();

        DeliveryNeed saved = savedCaptor.getValue();
        assertThat(saved.getAssignedFreelancerId()).isEqualTo(FREELANCER_ID);
        assertThat(saved.getDeliveryId()).isNull();
        assertThat(saved.getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);

        verify(deliveryNeedRepository).save(any());
    }
}
