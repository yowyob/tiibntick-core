package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that getCandidatesWithPricing loads real pickup/delivery coordinates
 * instead of the hardcoded (0.0, 0.0) that was present before fix/candidates-coordinates.
 *
 * <p>This test FAILS on origin/main (coordinates hardcoded to 0.0, price hardcoded to 0)
 * and PASSES on fix/candidates-coordinates (addresses loaded via addressUseCase, price
 * derived from haversine distance).
 */
@ExtendWith(MockitoExtension.class)
class DeliveryNeedApplicationServiceCandidatesTest {

    @Mock private IDeliveryNeedRepository deliveryNeedRepository;
    @Mock private AdminRelayPointUseCase adminRelayPointUseCase;
    @Mock private PushNotificationPort pushNotificationPort;
    @Mock private MatchingUseCase matchingUseCase;
    @Mock private AddressUseCase addressUseCase;
    @Mock private GofpUserRepository gofpUserRepository;
    @Mock private DatabaseClient databaseClient;

    private DeliveryNeedApplicationService service;

    // Real Yaoundé-area coordinates (Centre commercial Warda → Carrefour Bastos)
    private static final double PICKUP_LAT   = 3.866;
    private static final double PICKUP_LON   = 11.517;
    private static final double DELIVERY_LAT = 3.900;
    private static final double DELIVERY_LON = 11.520;

    private final UUID deliveryNeedId   = UUID.randomUUID();
    private final UUID pickupAddressId  = UUID.randomUUID();
    private final UUID deliveryAddressId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DeliveryNeedApplicationService(
                deliveryNeedRepository, adminRelayPointUseCase, pushNotificationPort,
                matchingUseCase, addressUseCase, gofpUserRepository, databaseClient);
    }

    @Test
    void getCandidatesWithPricing_usesRealCoordinatesFromAddressUseCase() {
        // Arrange — DeliveryNeed with non-null address IDs
        DeliveryNeed need = DeliveryNeed.builder()
                .id(deliveryNeedId)
                .userId(UUID.randomUUID())
                .pickupAddressId(pickupAddressId)
                .deliveryAddressId(deliveryAddressId)
                .title("Colis Yaoundé")
                .status(DeliveryNeedStatus.PENDING)
                .build();

        AddressDTO pickupDto = AddressDTO.builder()
                .id(pickupAddressId)
                .address(Address.builder()
                        .landmark("Centre commercial Warda")
                        .city("Yaoundé")
                        .country("Cameroun")
                        .coordinates(GeoCoordinates.of(PICKUP_LAT, PICKUP_LON))
                        .build())
                .build();

        AddressDTO deliveryDto = AddressDTO.builder()
                .id(deliveryAddressId)
                .address(Address.builder()
                        .landmark("Carrefour Bastos")
                        .city("Yaoundé")
                        .country("Cameroun")
                        .coordinates(GeoCoordinates.of(DELIVERY_LAT, DELIVERY_LON))
                        .build())
                .build();

        TopsisRankingUseCase.FreelancerCandidate candidate = TopsisRankingUseCase.FreelancerCandidate.builder()
                .freelancerId(UUID.randomUUID())
                .latitude(PICKUP_LAT)
                .longitude(PICKUP_LON)
                .rating(4.5)
                .topsisScore(0.85)
                .build();

        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressUseCase.getAddressById(pickupAddressId)).thenReturn(Mono.just(pickupDto));
        when(addressUseCase.getAddressById(deliveryAddressId)).thenReturn(Mono.just(deliveryDto));
        when(matchingUseCase.processMatchingForDeliveryNeed(
                any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(Mono.just(List.of(candidate)));

        // Act + assert response content
        StepVerifier.create(service.getCandidatesWithPricing(deliveryNeedId).collectList())
                .assertNext(dtos -> {
                    assertThat(dtos).hasSize(1);
                    FreelancerCandidateDTO dto = dtos.get(0);

                    // Price must be non-zero: on origin/main it was hardcoded to 0.0
                    assertThat(dto.getEstimatedPrice())
                            .as("estimatedPrice must be > 0 (derived from real haversine distance)")
                            .isGreaterThan(0.0);

                    // Breakdown must not be the hardcoded sentinel
                    assertThat(dto.getPriceBreakdown())
                            .as("priceBreakdown must reflect real distance, not the hardcoded 'Base: 0, Distance: 0'")
                            .isNotEqualTo("Base: 0, Distance: 0")
                            .startsWith("Base: 500, Distance: ");
                })
                .verifyComplete();

        // Assert matchingUseCase was called with the REAL coordinates, not (0.0, 0.0)
        verify(matchingUseCase).processMatchingForDeliveryNeed(
                eq(deliveryNeedId),
                eq(PICKUP_LAT),
                eq(PICKUP_LON),
                eq(DELIVERY_LAT),
                eq(DELIVERY_LON),
                eq(0.0),
                eq(null));
    }

    @Test
    void getCandidatesWithPricing_returnsEmptyWhenNoFreelancers() {
        DeliveryNeed need = DeliveryNeed.builder()
                .id(deliveryNeedId)
                .userId(UUID.randomUUID())
                .pickupAddressId(pickupAddressId)
                .deliveryAddressId(deliveryAddressId)
                .title("No-candidate need")
                .status(DeliveryNeedStatus.PENDING)
                .build();

        AddressDTO dto = AddressDTO.builder()
                .id(pickupAddressId)
                .address(Address.builder()
                        .landmark("Somewhere")
                        .city("Douala")
                        .country("Cameroun")
                        .coordinates(GeoCoordinates.of(4.061, 9.741))
                        .build())
                .build();

        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressUseCase.getAddressById(pickupAddressId)).thenReturn(Mono.just(dto));
        when(addressUseCase.getAddressById(deliveryAddressId)).thenReturn(Mono.just(dto));
        when(matchingUseCase.processMatchingForDeliveryNeed(
                any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.getCandidatesWithPricing(deliveryNeedId).collectList())
                .assertNext(dtos -> assertThat(dtos).isEmpty())
                .verifyComplete();
    }

    @Test
    void getCandidatesWithPricing_propagatesErrorWhenNeedNotFound() {
        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.empty());

        StepVerifier.create(service.getCandidatesWithPricing(deliveryNeedId))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
