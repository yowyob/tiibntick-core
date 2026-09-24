package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
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
    @Mock private GofpFreelancerRepository gofpFreelancerRepository;
    @Mock private FreelancerPricingPolicyUseCase freelancerPricingPolicyUseCase;
    @Mock private DatabaseClient databaseClient;

    private DeliveryNeedApplicationService service;

    // Real Yaoundé-area coordinates (Centre commercial Warda → Carrefour Bastos)
    private static final double PICKUP_LAT   = 3.866;
    private static final double PICKUP_LON   = 11.517;
    private static final double DELIVERY_LAT = 3.900;
    private static final double DELIVERY_LON = 11.520;

    private final UUID deliveryNeedId    = UUID.randomUUID();
    private final UUID pickupAddressId   = UUID.randomUUID();
    private final UUID deliveryAddressId = UUID.randomUUID();
    // Fixed owner so tests can pass a valid callerId (lot 25.1: callerId=null → 403)
    private final UUID ownerId           = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DeliveryNeedApplicationService(
                deliveryNeedRepository, adminRelayPointUseCase, pushNotificationPort,
                matchingUseCase, addressUseCase, gofpUserRepository,
                gofpFreelancerRepository, freelancerPricingPolicyUseCase, databaseClient);
    }

    @Test
    void getCandidatesWithPricing_usesRealCoordinatesFromAddressUseCase() {
        DeliveryNeed need = DeliveryNeed.builder()
                .id(deliveryNeedId)
                .userId(ownerId)
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
        // toPricedCandidate calls findById then findByCoreFreelancerId; stub both to empty
        // so it falls back to default pricing (acceptable for this coordinate-loading test)
        when(gofpFreelancerRepository.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(gofpFreelancerRepository.findByCoreFreelancerId(any(UUID.class))).thenReturn(Mono.empty());
        // priceCandidate calls getPolicy; stub to empty so DEFAULT_POLICY is used
        when(freelancerPricingPolicyUseCase.getPolicy(any())).thenReturn(Mono.empty());
        // priceCandidate calls findByCoreUserId for name; stub to empty → "Freelancer" fallback
        when(gofpUserRepository.findByCoreUserId(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.getCandidatesWithPricing(deliveryNeedId, ownerId).collectList())
                .assertNext(dtos -> {
                    assertThat(dtos).hasSize(1);
                    FreelancerCandidateDTO dto = dtos.get(0);
                    assertThat(dto.getEstimatedPrice())
                            .as("estimatedPrice must be > 0 (derived from real haversine distance)")
                            .isGreaterThan(0.0);
                    assertThat(dto.getPriceBreakdown())
                            .as("priceBreakdown must not be blank and must start with Base")
                            .isNotBlank()
                            .startsWith("Base");
                })
                .verifyComplete();

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
                .userId(ownerId)
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
        // No candidates → toPricedCandidate is never called; no freelancer repo stubs needed

        StepVerifier.create(service.getCandidatesWithPricing(deliveryNeedId, ownerId).collectList())
                .assertNext(dtos -> assertThat(dtos).isEmpty())
                .verifyComplete();
    }

    @Test
    void getCandidatesWithPricing_propagatesErrorWhenNeedNotFound() {
        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.empty());

        // Need not found → IllegalArgumentException before ownership check fires.
        // callerId value irrelevant here; using ownerId for consistency.
        StepVerifier.create(service.getCandidatesWithPricing(deliveryNeedId, ownerId))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
