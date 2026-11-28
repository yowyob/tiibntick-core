package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import com.yowyob.tiibntick.core.billing.pricing.application.service.PricingEngineService;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IBillingPolicyUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerEligibilityUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.query.NearbyCriteria;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.*;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.MatchingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase.FreelancerCandidate;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpCourierPricingProperties;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpDeliveryOtpProperties;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerVehicle;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot C-54 — end to end in the services, with the real billing engine, the real matching and TOPSIS
 * and the real courier card: two couriers (moto, taxi) get two different prices for the same parcel;
 * a "taxi" need only proposes — and only shows its announcement to — the taxi; applications come back
 * available-and-loyal first, available-and-new next, busy last; the profile creation date never
 * reaches a sender.
 */
@DisplayName("C-54 — price per vehicle, required vehicle, courier card and ranking")
class CourierVehicleJourneyTest {

    static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    static final double PICKUP_LAT = 4.0511, PICKUP_LON = 9.7679;
    static final double DROP_LAT = 4.0600, DROP_LON = 9.7800;

    final UUID tenantId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final UUID needId = UUID.randomUUID();
    final UUID announcementId = UUID.randomUUID();
    final UUID pickupAddressId = UUID.randomUUID();
    final UUID dropAddressId = UUID.randomUUID();

    // couriers: gofp id, core user id
    final UUID moto = UUID.randomUUID(), motoUser = UUID.randomUUID();
    final UUID taxi = UUID.randomUUID(), taxiUser = UUID.randomUUID();
    final UUID loyal = UUID.randomUUID();
    final UUID busy = UUID.randomUUID();
    final Instant motoCreatedAt = Instant.parse("2026-11-01T08:30:00Z");

    final Map<UUID, GofpFreelancer> couriers = new HashMap<>();
    final Map<UUID, FreelancerVehicle> vehicles = new HashMap<>();
    final Map<UUID, Long> delivered = new HashMap<>();
    final Map<UUID, Long> unfinished = new HashMap<>();
    final Map<UUID, DeliveryNeed> needs = new HashMap<>();
    final Map<UUID, Announcement> mirror = new HashMap<>();
    final List<UUID> near = new ArrayList<>();

    final GofpFreelancerRepository freelancerRepo = mock(GofpFreelancerRepository.class);
    final DeliveryRepository deliveryRepo = mock(DeliveryRepository.class);
    final IDeliveryAnnouncementPort port = mock(IDeliveryAnnouncementPort.class);
    List<AnnouncementResponseSnapshot> responses = List.of();
    String announcedVehicle;

    DeliveryNeedApplicationService needService;
    AnnouncementApplicationService announcementService;

    @BeforeEach
    void setUp() {
        couriers.put(moto, courier(moto, motoUser, motoCreatedAt));
        couriers.put(taxi, courier(taxi, taxiUser, NOW.minus(30, ChronoUnit.DAYS)));
        couriers.put(loyal, courier(loyal, UUID.randomUUID(), NOW.minus(400, ChronoUnit.DAYS)));
        couriers.put(busy, courier(busy, UUID.randomUUID(), NOW.minus(400, ChronoUnit.DAYS)));
        vehicles.put(moto, vehicle(moto, "MOTORBIKE", "LT 111 AA"));
        vehicles.put(taxi, vehicle(taxi, "CAR", "CE 222 BB"));
        vehicles.put(loyal, vehicle(loyal, "MOTORBIKE", "LT 333 CC"));
        vehicles.put(busy, vehicle(busy, "MOTORBIKE", "LT 444 DD"));
        delivered.put(loyal, 60L);
        delivered.put(busy, 80L);
        unfinished.put(busy, 1L);

        when(freelancerRepo.findById(any(UUID.class))).thenAnswer(i -> Mono.justOrEmpty(couriers.get(i.<UUID>getArgument(0))));
        when(freelancerRepo.findByCoreFreelancerId(any())).thenReturn(Mono.empty());
        when(freelancerRepo.findByCoreUserId(any())).thenAnswer(i -> Mono.justOrEmpty(couriers.values().stream()
                .filter(c -> c.getCoreUserId().equals(i.getArgument(0))).findFirst()));
        FreelancerVehicleRepository vehicleRepo = mock(FreelancerVehicleRepository.class);
        when(vehicleRepo.findByFreelancerId(any())).thenAnswer(i -> Mono.justOrEmpty(vehicles.get(i.<UUID>getArgument(0))));
        when(deliveryRepo.countDeliveredByFreelancerIds(any())).thenAnswer(i -> Mono.just(
                i.<java.util.Collection<UUID>>getArgument(0).stream().mapToLong(id -> delivered.getOrDefault(id, 0L)).sum()));
        when(deliveryRepo.countUnfinishedByFreelancerIds(any())).thenAnswer(i -> Mono.just(
                i.<java.util.Collection<UUID>>getArgument(0).stream().mapToLong(id -> unfinished.getOrDefault(id, 0L)).sum()));
        when(deliveryRepo.sumOccupiedVolumeM3ByFreelancerId(any())).thenReturn(Mono.just(0.0));

        CourierCardService cards = new CourierCardService(freelancerRepo, vehicleRepo, deliveryRepo,
                new GofpCourierPricingProperties()).withClock(Clock.fixed(NOW, ZoneOffset.UTC));

        // real billing engine, no personal policy: every price comes from the platform grids
        IBillingPolicyUseCase billing = mock(IBillingPolicyUseCase.class);
        when(billing.findByOwnerActorId(anyString())).thenReturn(Flux.empty());
        FreelancerPriceQuoteService quotes = new FreelancerPriceQuoteService(billing, new PricingEngineService(null,
                new DslCompilerService(new DslLexer(), new DslParser()), new DslEvaluator()));

        // real matching + TOPSIS
        IFreelancerProviderPort provider = mock(IFreelancerProviderPort.class);
        when(provider.findActiveCandidatesNear(anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(i -> Mono.just(near.stream().map(this::candidate).toList()));
        MatchingUseCase matching = new MatchingUseCase(new TopsisRankingUseCase(), port,
                mock(IGeolocationPort.class), vehicleRepo, deliveryRepo,
                new FreelancerVehicleApplicationService(), freelancerRepo, provider,
                mock(IEventPublisherPort.class), mock(NotificationService.class), new ObjectMapper());
        matching.setCourierCards(cards);

        IDeliveryNeedRepository needRepo = mock(IDeliveryNeedRepository.class);
        when(needRepo.findById(any(UUID.class))).thenAnswer(i -> Mono.justOrEmpty(needs.get(i.<UUID>getArgument(0))));
        AddressUseCase addresses = mock(AddressUseCase.class);
        when(addresses.getAddressById(pickupAddressId)).thenReturn(Mono.just(address(pickupAddressId, PICKUP_LAT, PICKUP_LON)));
        when(addresses.getAddressById(dropAddressId)).thenReturn(Mono.just(address(dropAddressId, DROP_LAT, DROP_LON)));
        FreelancerEligibilityUseCase eligibility = mock(FreelancerEligibilityUseCase.class);
        when(eligibility.isEligible(any(), any())).thenReturn(Mono.just(true));
        GofpUserRepository users = mock(GofpUserRepository.class);
        when(users.findByCoreUserId(any())).thenReturn(Mono.empty());

        needService = new DeliveryNeedApplicationService(needRepo, mock(AdminRelayPointUseCase.class),
                mock(PushNotificationPort.class), matching, addresses, users, freelancerRepo, quotes,
                eligibility);
        needService.setCourierCards(cards);
        needService.setDeliveries(deliveryRepo);
        IAnnouncementRepository mirrorRepo = mock(IAnnouncementRepository.class);
        when(mirrorRepo.findById(any(UUID.class))).thenAnswer(i -> Mono.justOrEmpty(mirror.get(i.<UUID>getArgument(0))));
        needService.setLocalAnnouncements(mirrorRepo);

        TenantContextHolder tenant = mock(TenantContextHolder.class);
        when(tenant.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(port.findById(eq(tenantId), eq(announcementId))).thenAnswer(i -> Mono.just(snapshot()));
        when(port.findOpenAnnouncementsNear(eq(tenantId), any())).thenAnswer(i -> Flux.just(snapshot()));
        announcementService = new AnnouncementApplicationService(port, tenant, mock(FreelancerQuotaService.class),
                mock(INegotiationChatPort.class),
                freelancerRepo, users, deliveryRepo, mock(DeliveryOtpService.class),
                mock(GofpDeliveryOtpProperties.class), com.yowyob.tiibntick.core.gofreelancer.TestDeliveries.termsRecorder());
        announcementService.setCourierCards(cards);
    }

    // ── fixtures ────────────────────────────────────────────────────────

    private GofpFreelancer courier(UUID id, UUID user, Instant createdAt) {
        return GofpFreelancer.builder().id(id).coreUserId(user).isActive(true).rating(4.0)
                .remainingDeliveries(10).latitudeGps((float) PICKUP_LAT).longitudeGps((float) PICKUP_LON)
                .createdAt(createdAt).build();
    }

    private static FreelancerVehicle vehicle(UUID courier, String type, String plate) {
        return FreelancerVehicle.builder().id(UUID.randomUUID()).freelancerId(courier).vehicleType(type)
                .plateNumber(plate).photoUrls("uploads/images/vehicule_" + courier + ".jpg").build();
    }

    private static AddressDTO address(UUID id, double lat, double lon) {
        return AddressDTO.builder().id(id).address(Address.builder().street("rue").city("Douala").country("CM")
                .coordinates(GeoCoordinates.of(lat, lon)).build()).build();
    }

    private FreelancerCandidate candidate(UUID id) {
        return FreelancerCandidate.builder().freelancerId(id).latitude(PICKUP_LAT).longitude(PICKUP_LON)
                .rating(4.0).remainingDeliveries(10).build();
    }

    private DeliveryNeed need(String requiredVehicle) {
        DeliveryNeed n = DeliveryNeed.builder().id(needId).userId(ownerId).pickupAddressId(pickupAddressId)
                .deliveryAddressId(dropAddressId).distance(10.0).requiredVehicleType(requiredVehicle).build();
        needs.put(needId, n);
        return n;
    }

    private AnnouncementSnapshot snapshot() {
        return new AnnouncementSnapshot(
                announcementId, tenantId, ownerId, "Colis", "desc", AnnouncementStatus.PUBLISHED,
                NOW, NOW, null, "XAF", "QUOTE_REQUEST",
                Address.builder().street("rue").city("Douala").country("CM")
                        .coordinates(GeoCoordinates.of(PICKUP_LAT, PICKUP_LON)).build(),
                Address.builder().street("rue").city("Douala").country("CM").build(), 0.0,
                null, null, null, null, null, null, null, null,
                "Destinataire", "+237600000000", null, null, null, responses,
                new com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementExtras(
                        null, null, null, null, null, announcedVehicle, null, null,
                        java.math.BigDecimal.TEN, null, null, null, null, "DELIVERY_NEED", needId));
    }

    private void announcement(String requiredVehicle) {
        Announcement a = new Announcement();
        a.setId(announcementId);
        a.setDeliveryNeedId(needId);
        a.setDistance(10.0);
        a.setRequiredVehicleType(requiredVehicle);
        mirror.put(announcementId, a);
        announcedVehicle = requiredVehicle;
    }

    private List<FreelancerCandidateDTO> candidates() {
        return needService.getCandidatesWithPricing(needId, ownerId).collectList().block();
    }

    // ── A2 — platform grid of the courier's vehicle ─────────────────────

    @Test
    @DisplayName("same parcel, 10 km: the moto courier costs 1600 XAF (600 + 10 × 100), the taxi 3000 (1000 + 10 × 200)")
    void motoAndTaxiGetTheirOwnGrid() {
        need(null);
        near.add(moto);
        near.add(taxi);

        Map<UUID, Double> prices = new HashMap<>();
        candidates().forEach(c -> prices.put(c.getFreelancerId(), c.getEstimatedPrice()));

        assertThat(prices).containsEntry(moto, 1600.0).containsEntry(taxi, 3000.0);
    }

    // ── A3 — required vehicle ───────────────────────────────────────────

    @Test
    @DisplayName("a need requiring a taxi only proposes the taxi")
    void requiredTaxiProposesOnlyTheTaxi() {
        need("CAR");
        near.add(moto);
        near.add(taxi);

        assertThat(candidates()).extracting(FreelancerCandidateDTO::getFreelancerId).containsExactly(taxi);
    }

    @Test
    @DisplayName("an announcement requiring a taxi is shown in « Disponibles » to the taxi, not to the moto")
    void requiredTaxiAnnouncementOnlyVisibleToTaxi() {
        announcement("CAR");
        NearbyCriteria criteria = mock(NearbyCriteria.class);

        List<AnnouncementResponseDTO> forTaxi = announcementService.getNearbyAnnouncementsFor(criteria, taxiUser)
                .collectList().block();
        List<AnnouncementResponseDTO> forMoto = announcementService.getNearbyAnnouncementsFor(criteria, motoUser)
                .collectList().block();

        assertThat(forTaxi).extracting(AnnouncementResponseDTO::getId).containsExactly(announcementId);
        assertThat(forMoto).isEmpty();
    }

    @Test
    @DisplayName("an announcement with no required vehicle is shown to every courier")
    void anyVehicleAnnouncementVisibleToAll() {
        announcement(null);
        NearbyCriteria criteria = mock(NearbyCriteria.class);
        assertThat(announcementService.getNearbyAnnouncementsFor(criteria, motoUser).collectList().block()).hasSize(1);
        assertThat(announcementService.getNearbyAnnouncementsFor(criteria, taxiUser).collectList().block()).hasSize(1);
    }

    // ── A4 — courier card ───────────────────────────────────────────────

    @Test
    @DisplayName("the card shows the real vehicle, plate, photos, delivered count and badge — never the sign-up date")
    void cardCarriesTheVehicleAndNotTheSignUpDate() throws Exception {
        need(null);
        near.add(moto);

        FreelancerCandidateDTO card = candidates().get(0);
        assertThat(card.getVehicleType()).isEqualTo("MOTORBIKE");
        assertThat(card.getVehiclePlate()).isEqualTo("LT 111 AA");
        assertThat(card.getVehiclePhotoUrls()).containsExactly("uploads/images/vehicule_" + moto + ".jpg");
        assertThat(card.getDeliveredCount()).isZero();
        assertThat(card.getBadge()).isEqualTo(CourierCardService.BADGE_NEW);

        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        String candidateJson = json.writeValueAsString(card);
        responses = List.of(new AnnouncementResponseSnapshot(UUID.randomUUID(), moto, new BigDecimal("2000"),
                "XAF", "SENT", NOW));
        announcement(null);
        String applicationsJson = json.writeValueAsString(
                announcementService.getSubscriptionsForAnnouncement(announcementId).collectList().block());
        for (String body : List.of(candidateJson, applicationsJson)) {
            assertThat(body).doesNotContain("2026-11-01").doesNotContain(String.valueOf(motoCreatedAt.getEpochSecond()))
                    .doesNotContainIgnoringCase("memberSince").doesNotContainIgnoringCase("registeredAt")
                    .doesNotContainIgnoringCase("seniority").doesNotContainIgnoringCase("loyaltyScore");
        }
        assertThat(applicationsJson).contains("LT 111 AA").contains("\"badge\":\"NEW\"");
    }

    // ── A5 — ranking of the applications ────────────────────────────────

    @Test
    @DisplayName("applications: available and loyal first, available and new next, busy last (whatever the core order)")
    void applicationsSortedByAvailabilityThenLoyalty() {
        responses = List.of(
                new AnnouncementResponseSnapshot(UUID.randomUUID(), busy, new BigDecimal("1000"), "XAF", "SENT", NOW),
                new AnnouncementResponseSnapshot(UUID.randomUUID(), moto, new BigDecimal("2000"), "XAF", "SENT", NOW),
                new AnnouncementResponseSnapshot(UUID.randomUUID(), loyal, new BigDecimal("3000"), "XAF", "SENT", NOW));
        announcement(null);

        List<SubscriptionResponseDTO> list = announcementService.getSubscriptionsForAnnouncement(announcementId)
                .collectList().block();

        assertThat(list).extracting(SubscriptionResponseDTO::getFreelancerId).containsExactly(loyal, moto, busy);
        assertThat(list).extracting(SubscriptionResponseDTO::getCourierStatus)
                .containsExactly("AVAILABLE", "AVAILABLE", "BUSY");
        assertThat(list).extracting(SubscriptionResponseDTO::getBadge).containsExactly("LOYAL", "NEW", "LOYAL");
    }

    // ── B4 — indicative price in "Apply" ─────────────────────────────────

    @Test
    @DisplayName("« Postuler »: the taxi sees the taxi grid price of the announced parcel, the moto the moto price")
    void platformPriceForTheApplyingCourier() {
        need(null);
        announcement(null);
        assertThat(needService.platformPriceForCourier(announcementId, taxiUser).block().totalPrice()).isEqualTo(3000.0);
        var forMoto = needService.platformPriceForCourier(announcementId, motoUser).block();
        assertThat(forMoto.totalPrice()).isEqualTo(1600.0);
        assertThat(forMoto.vehicleCategory()).isEqualTo("moto");
    }
}
