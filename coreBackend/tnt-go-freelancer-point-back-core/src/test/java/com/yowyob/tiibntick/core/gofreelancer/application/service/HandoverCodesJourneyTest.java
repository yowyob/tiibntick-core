package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryNeedRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.HandoverCodesDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerEligibilityUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPriceQuoteUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.*;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.CoreDeliveryState;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.MatchingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpDeliveryOtpProperties;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.CodedStatusException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.InvalidHandoverCodeException;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordCustodyTransferUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordMissionUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordPaymentUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot C-53 — the handover codes reach the sender. Real need, announcement, invitation, OTP, handover
 * code and status services; delivery-core, the trust ledger and the stores are simulated (deliveries
 * and needs in memory, so that what one step writes is what the next one reads).
 *
 * <p>Journey: invitation accepted (election) → the owner reads both codes → PICKED_UP with a wrong
 * code (refused, {@code INVALID_HANDOVER_CODE}) then with the code read → DELIVERED with the code read.
 */
@DisplayName("C-53 — handover codes, from election to delivery")
class HandoverCodesJourneyTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID courierUserId = UUID.randomUUID();
    private final UUID announcementId = UUID.randomUUID();
    private final UUID freelancerId = UUID.randomUUID();
    private final UUID gofpFreelancerId = UUID.randomUUID();
    private final UUID responseId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();

    private final IDeliveryAnnouncementPort port = mock(IDeliveryAnnouncementPort.class);
    private final TenantContextHolder tenant = mock(TenantContextHolder.class);
    private final IDeliveryNeedRepository needs = mock(IDeliveryNeedRepository.class);
    private final AddressUseCase addressUseCase = mock(AddressUseCase.class);
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final FreelancerPriceQuoteUseCase quotes = mock(FreelancerPriceQuoteUseCase.class);
    private final IDeliveryLifecyclePort lifecycle = mock(IDeliveryLifecyclePort.class);
    private final PushNotificationPort push = mock(PushNotificationPort.class);

    private final Map<UUID, Announcement> mirror = new HashMap<>();
    private final Map<UUID, DeliveryNeed> needStore = new HashMap<>();
    private final Map<UUID, Delivery> deliveryStore = new HashMap<>();
    private final Map<UUID, NeedInvitation> invitationStore = new HashMap<>();

    private final OtpService otpService = new OtpService();
    private DeliveryNeedApplicationService needService;
    private NeedInvitationService invitationService;
    private HandoverCodeService handoverCodes;
    private DeliveryStatusApplicationService statusService;
    private boolean responded;

    @BeforeEach
    void setUp() {
        IAnnouncementRepository mirrorRepo = mock(IAnnouncementRepository.class);
        when(mirrorRepo.save(any())).thenAnswer(inv -> {
            Announcement a = inv.getArgument(0);
            mirror.put(a.getId(), a);
            return Mono.just(a);
        });
        when(mirrorRepo.findById(any(UUID.class))).thenAnswer(inv -> Mono.justOrEmpty(mirror.get(inv.getArgument(0))));
        when(mirrorRepo.findByDeliveryNeedId(any())).thenAnswer(inv -> Mono.justOrEmpty(mirror.values().stream()
                .filter(a -> inv.getArgument(0).equals(a.getDeliveryNeedId())).findFirst()));

        when(needs.save(any())).thenAnswer(inv -> {
            DeliveryNeed n = inv.getArgument(0);
            needStore.put(n.getId(), n);
            return Mono.just(n);
        });
        when(needs.findById(any(UUID.class))).thenAnswer(inv -> Mono.justOrEmpty(needStore.get(inv.getArgument(0))));
        when(tenant.currentTenantId()).thenReturn(Mono.just(tenantId));

        when(deliveries.save(any())).thenAnswer(inv -> {
            Delivery d = inv.getArgument(0);
            deliveryStore.put(d.getId(), d);
            return Mono.just(d);
        });
        when(deliveries.findById(any(UUID.class))).thenAnswer(inv -> Mono.justOrEmpty(deliveryStore.get(inv.getArgument(0))));
        when(deliveries.findByDeliveryNeedId(any())).thenAnswer(inv -> Mono.justOrEmpty(deliveryStore.values().stream()
                .filter(d -> inv.getArgument(0).equals(d.getDeliveryNeedId())).findFirst()));
        when(push.sendPushNotification(any(), anyString(), anyString())).thenReturn(Mono.empty());

        DeliveryOtpService deliveryOtp = new DeliveryOtpService(otpService, deliveries, needs, mirrorRepo, push,
                mock(EmailPort.class), new HandoverCodeCipher("journey-test-secret"));

        NeedAnnouncementService link = new NeedAnnouncementService(port, tenant, mirrorRepo, needs, freelancers);
        GofpUserRepository users = mock(GofpUserRepository.class);
        when(users.findByCoreUserId(any())).thenReturn(Mono.empty());
        needService = new DeliveryNeedApplicationService(
                needs, mock(AdminRelayPointUseCase.class), push,
                mock(MatchingUseCase.class), addressUseCase, users,
                freelancers, quotes, mock(FreelancerEligibilityUseCase.class), legacyUserExists());
        needService.setNeedAnnouncements(link);
        needService.setDeliveries(deliveries);
        needService.setDeliveryOtp(deliveryOtp);
        AnnouncementApplicationService announcementService = new AnnouncementApplicationService(
                port, tenant, mock(FreelancerQuotaService.class),
                mock(INegotiationChatPort.class), mirrorRepo, mock(AnnouncementSubscriptionRepository.class),
                freelancers, mock(GofpUserRepository.class), deliveries, deliveryOtp,
                mock(GofpDeliveryOtpProperties.class));
        announcementService.setNeedAnnouncements(link);
        needService.setAnnouncements(announcementService);

        invitationService = new NeedInvitationService(needs, freelancers, invitationRepo(), needService);
        handoverCodes = new HandoverCodeService(needs, deliveries, deliveryOtp);

        ISettlementUseCase settlements = mock(ISettlementUseCase.class);
        when(settlements.record(any())).thenReturn(Mono.empty());
        RecordCustodyTransferUseCase custody = mock(RecordCustodyTransferUseCase.class);
        when(custody.record(any())).thenReturn(Mono.empty());
        RecordMissionUseCase missions = mock(RecordMissionUseCase.class);
        when(missions.recordCreated(any(), any(), any(), anyInt())).thenReturn(Mono.empty());
        when(missions.recordCompleted(any(), any(), any())).thenReturn(Mono.empty());
        DeliveryQueryUseCase query = mock(DeliveryQueryUseCase.class);
        when(query.resolveActorIdForDeliveryPerson(any(), any())).thenReturn(Mono.empty());
        statusService = new DeliveryStatusApplicationService(
                deliveries, mock(RelayDepositService.class), mock(GofpRelayPointRepository.class),
                push, needs, mirrorRepo, otpService, deliveryOtp, settlements,
                custody, missions, mock(RecordPaymentUseCase.class),
                query, lifecycle, tenant, new SimpleMeterRegistry());
        statusService.initMetrics();

        when(addressUseCase.createAddress(any())).thenAnswer(inv -> {
            AddressDTO in = inv.getArgument(0);
            return Mono.just(AddressDTO.builder().id(UUID.randomUUID()).address(in.getAddress()).build());
        });
        when(addressUseCase.getAddressById(any())).thenReturn(Mono.empty());
        when(port.publish(any())).thenAnswer(inv -> {
            PublishAnnouncementPortCommand c = inv.getArgument(0);
            return Mono.just(snapshot(c.clientId(), null, null, List.of()));
        });
        when(port.findById(eq(tenantId), eq(announcementId))).thenAnswer(inv -> Mono.just(
                snapshot(ownerId, null, null, responded ? List.of(response()) : List.of())));
        when(port.respond(any())).thenAnswer(inv -> {
            responded = true;
            return Mono.just(snapshot(ownerId, null, null, List.of(response())));
        });
        when(port.selectResponse(tenantId, announcementId, ownerId, responseId)).thenReturn(Mono.just(
                snapshot(ownerId, responseId, deliveryId, List.of(response()))));
        when(quotes.quote(any(), any(), any())).thenReturn(Mono.just(
                new FreelancerPriceQuoteUseCase.Quote(3000, "XAF", "grille", "TEST", null)));

        when(freelancers.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(freelancers.findByCoreFreelancerId(any())).thenReturn(Mono.empty());
        when(freelancers.findByCoreUserId(any())).thenReturn(Mono.empty());
        GofpFreelancer courier = GofpFreelancer.builder().id(gofpFreelancerId).coreFreelancerId(freelancerId)
                .coreUserId(courierUserId).status(FreelancerStatus.APPROVED).isActive(true).build();
        when(freelancers.findById(gofpFreelancerId)).thenReturn(Mono.just(courier));
        when(freelancers.findByCoreFreelancerId(freelancerId)).thenReturn(Mono.just(courier));
        when(freelancers.findByCoreUserId(courierUserId)).thenReturn(Mono.just(courier));
    }

    // ── harness ─────────────────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DatabaseClient legacyUserExists() {
        DatabaseClient db = mock(DatabaseClient.class);
        DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class);
        RowsFetchSpec rows = mock(RowsFetchSpec.class);
        org.springframework.r2dbc.core.FetchSpec fetch = mock(org.springframework.r2dbc.core.FetchSpec.class);
        when(db.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.map(any(java.util.function.BiFunction.class))).thenReturn(rows);
        when(rows.first()).thenReturn(Mono.just(1));
        when(spec.fetch()).thenReturn(fetch);
        when(fetch.rowsUpdated()).thenReturn(Mono.just(1L));
        return db;
    }

    private NeedInvitationRepository invitationRepo() {
        return new NeedInvitationRepository() {
            @Override public Mono<NeedInvitation> save(NeedInvitation i) {
                invitationStore.put(i.getId(), i);
                return Mono.just(i);
            }
            @Override public Mono<NeedInvitation> findById(UUID id) {
                return Mono.justOrEmpty(invitationStore.get(id));
            }
            @Override public Flux<NeedInvitation> findAllByDeliveryNeedId(UUID n) {
                return Flux.fromIterable(invitationStore.values()).filter(i -> n.equals(i.getDeliveryNeedId()));
            }
            @Override public Flux<NeedInvitation> findAllByFreelancerIdAndStatus(UUID f, String st) {
                return Flux.fromIterable(invitationStore.values())
                        .filter(i -> f.equals(i.getFreelancerId()) && st.equals(i.getStatus()));
            }
        };
    }

    private AnnouncementResponseSnapshot response() {
        return new AnnouncementResponseSnapshot(responseId, freelancerId, new BigDecimal("3000"), "XAF",
                "SENT", Instant.now());
    }

    private AnnouncementSnapshot snapshot(UUID clientId, UUID selected, UUID createdDelivery,
                                          List<AnnouncementResponseSnapshot> responses) {
        return new AnnouncementSnapshot(
                announcementId, tenantId, clientId, "Colis", "desc",
                selected != null ? AnnouncementStatus.ASSIGNED : AnnouncementStatus.PUBLISHED,
                Instant.now(), Instant.now(), null, "XAF", "QUOTE_REQUEST",
                Address.builder().street("Rue Joss").city("Douala").country("CM")
                        .coordinates(GeoCoordinates.of(4.0511, 9.7679)).build(),
                Address.builder().landmark("Carrefour Ndokoti").city("Douala").country("CM").build(), 0.0,
                null, null, null, null, null, null, null, null,
                "Destinataire", "+237600000000", selected, createdDelivery, null, responses);
    }

    private UUID createNeed() {
        DeliveryNeedRequestDTO r = new DeliveryNeedRequestDTO();
        r.setTitle("Colis vêtements");
        r.setDescription("2 kg");
        r.setPaymentMethod("CASH");
        r.setPickupAddress(AddressDTO.builder().address(Address.builder().street("Rue Joss").city("Douala")
                .country("CM").coordinates(GeoCoordinates.of(4.0511, 9.7679)).build()).build());
        r.setDeliveryAddress(AddressDTO.builder().address(Address.builder().landmark("Carrefour Ndokoti")
                .city("Douala").country("CM").build()).build());
        return needService.createDeliveryNeed(r, ownerId).block().getId();
    }

    /** The owner invites the courier, the courier accepts: the election. Returns what the courier got. */
    private DeliveryNeedResponseDTO inviteAndAccept(UUID needId) {
        UUID invId = UUID.randomUUID();
        invitationStore.put(invId, NeedInvitation.builder()
                .id(invId).deliveryNeedId(needId).freelancerId(gofpFreelancerId).invitedBy(ownerId)
                .status("OPEN").expiresAt(Instant.now().plusSeconds(600)).createdAt(Instant.now()).build());
        return invitationService.accept(invId, courierUserId).block();
    }

    private void coreIs(String status) {
        when(lifecycle.findState(tenantId, deliveryId)).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, status, "TNT-1", freelancerId, null, null)));
    }

    private static DeliveryStatusUpdateDTO statusWith(DeliveryStatus status, String code) {
        DeliveryStatusUpdateDTO d = new DeliveryStatusUpdateDTO();
        d.setStatus(status);
        d.setConfirmationCode(code);
        return d;
    }

    private static String other(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    // ── journey ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("election → owner reads both codes → wrong code refused → PICKED_UP and DELIVERED with the codes read")
    void fullJourney() {
        UUID needId = createNeed();

        inviteAndAccept(needId);

        Delivery created = deliveryStore.get(deliveryId);
        assertThat(created).as("the election created the gofp delivery").isNotNull();
        assertThat(created.getPickupOtpHash()).as("pickup code created at election").isNotNull();
        assertThat(created.getDeliveryOtpHash()).as("delivery code created at election").isNotNull();
        assertThat(created.getPickupCodeEnc()).as("kept encrypted").startsWith("v1:");

        HandoverCodesDTO codes = handoverCodes.forOwner(needId, ownerId).block();
        assertThat(codes.pickupCode()).matches("\\d{6}");
        assertThat(codes.deliveryCode()).matches("\\d{6}");
        assertThat(codes.deliveryStatus()).isEqualTo("CREATED");
        assertThat(created.getPickupCodeEnc()).as("the plain code is never stored").doesNotContain(codes.pickupCode());

        // Wrong code: refused with the stable code, nothing confirmed in delivery-core.
        coreIs("CREATED");
        StepVerifier.create(statusService.updateStatus(deliveryId,
                        statusWith(DeliveryStatus.PICKED_UP, other(codes.pickupCode()))))
                .expectError(InvalidHandoverCodeException.class)
                .verify();

        when(lifecycle.confirmPickup(tenantId, deliveryId, freelancerId)).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "PICKED_UP", "TNT-1", freelancerId, Instant.now(), null)));
        Delivery picked = statusService.updateStatus(deliveryId,
                statusWith(DeliveryStatus.PICKED_UP, codes.pickupCode())).block();
        assertThat(picked.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);

        HandoverCodesDTO afterPickup = handoverCodes.forOwner(needId, ownerId).block();
        assertThat(afterPickup.pickupCode()).as("pickup code gone once picked up").isNull();
        assertThat(afterPickup.deliveryCode()).as("same delivery code, not re-issued").isEqualTo(codes.deliveryCode());
        assertThat(afterPickup.deliveryStatus()).isEqualTo("PICKED_UP");

        coreIs("PICKED_UP");
        when(lifecycle.startOrResumeTransit(tenantId, deliveryId, freelancerId)).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "IN_TRANSIT", "TNT-1", freelancerId, Instant.now(), null)));
        when(lifecycle.complete(tenantId, deliveryId, freelancerId)).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "DELIVERED", "TNT-1", freelancerId, Instant.now(), Instant.now())));
        Delivery delivered = statusService.updateStatus(deliveryId,
                statusWith(DeliveryStatus.DELIVERED, codes.deliveryCode())).block();
        assertThat(delivered.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);

        HandoverCodesDTO afterDelivery = handoverCodes.forOwner(needId, ownerId).block();
        assertThat(afterDelivery.pickupCode()).isNull();
        assertThat(afterDelivery.deliveryCode()).as("no code once delivered").isNull();
        assertThat(afterDelivery.deliveryStatus()).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("the elected courier learns no code: not in the acceptance answer, 403 on the owner route")
    void courierLearnsNothing() throws Exception {
        UUID needId = createNeed();

        DeliveryNeedResponseDTO toCourier = inviteAndAccept(needId);
        HandoverCodesDTO codes = handoverCodes.forOwner(needId, ownerId).block();

        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(toCourier);
        assertThat(json).doesNotContain(codes.pickupCode()).doesNotContain(codes.deliveryCode());

        String deliveryJson = new ObjectMapper().registerModule(new JavaTimeModule())
                .writeValueAsString(deliveryStore.get(deliveryId));
        assertThat(deliveryJson).as("hashes and encrypted codes never serialised")
                .doesNotContain("OtpHash").doesNotContain("CodeEnc").doesNotContain("v1:");

        StepVerifier.create(handoverCodes.forOwner(needId, courierUserId))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    @DisplayName("owner route: another user → 403, no caller → 403, unknown need → 404, no courier yet → 409 NO_COURIER_YET")
    void ownerRouteRefusals() {
        UUID needId = createNeed();

        StepVerifier.create(handoverCodes.forOwner(needId, UUID.randomUUID()))
                .expectError(AccessDeniedException.class).verify();
        StepVerifier.create(handoverCodes.forOwner(needId, null))
                .expectError(AccessDeniedException.class).verify();
        StepVerifier.create(handoverCodes.forOwner(UUID.randomUUID(), ownerId))
                .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("not found"))
                .verify();
        StepVerifier.create(handoverCodes.forOwner(needId, ownerId))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(CodedStatusException.class);
                    assertThat(((CodedStatusException) e).getCode()).isEqualTo("NO_COURIER_YET");
                    assertThat(((CodedStatusException) e).getStatusCode().value()).isEqualTo(409);
                })
                .verify();
    }

    @Test
    @DisplayName("delivery from before C-53 (hash only): reading re-issues the codes still useful, and they verify")
    void legacyDelivery_codesReissued() {
        UUID needId = createNeed();
        Delivery legacy = new Delivery();
        legacy.setId(deliveryId);
        legacy.setDeliveryNeedId(needId);
        legacy.setFreelancerId(freelancerId);
        legacy.setStatus(DeliveryStatus.PICKED_UP);
        legacy.setPickupOtpHash(otpService.hashOtp("123456"));
        String oldDeliveryHash = otpService.hashOtp("654321");
        legacy.setDeliveryOtpHash(oldDeliveryHash);
        deliveryStore.put(deliveryId, legacy);

        HandoverCodesDTO codes = handoverCodes.forOwner(needId, ownerId).block();

        assertThat(codes.pickupCode()).as("already picked up: not re-issued").isNull();
        assertThat(codes.deliveryCode()).matches("\\d{6}");
        Delivery saved = deliveryStore.get(deliveryId);
        assertThat(saved.getDeliveryOtpHash()).isNotEqualTo(oldDeliveryHash);
        assertThat(otpService.verifyOtp(codes.deliveryCode(), saved.getDeliveryOtpHash())).isTrue();
        assertThat(saved.getDeliveryCodeEnc()).startsWith("v1:");
        assertThat(saved.getPickupCodeEnc()).as("pickup not re-issued after pickup").isNull();

        HandoverCodesDTO again = handoverCodes.forOwner(needId, ownerId).block();
        assertThat(again.deliveryCode()).as("read back, not re-issued twice").isEqualTo(codes.deliveryCode());
    }

    @Test
    @DisplayName("cancelled or failed: no code is returned")
    void finishedDelivery_noCode() {
        UUID needId = createNeed();
        inviteAndAccept(needId);
        deliveryStore.get(deliveryId).setStatus(DeliveryStatus.CANCELLED);

        HandoverCodesDTO codes = handoverCodes.forOwner(needId, ownerId).block();

        assertThat(codes.pickupCode()).isNull();
        assertThat(codes.deliveryCode()).isNull();
        assertThat(codes.deliveryStatus()).isEqualTo("CANCELLED");
    }
}
