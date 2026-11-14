package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryNeedRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerEligibilityUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPriceQuoteUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.*;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.MatchingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpDeliveryOtpProperties;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * un besoin Go publie son annonce QUOTE_REQUEST, et besoin ↔ annonce
 * restent un seul état de vérité.
 *
 * <p>Vrais {@link DeliveryNeedApplicationService}, {@link AnnouncementApplicationService} et
 * {@link NeedAnnouncementService} ; le port vers tnt-delivery-core et les dépôts sont simulés
 * (le miroir local d'annonce est un dépôt en mémoire, pour que le lien besoin → annonce soit relu).
 */
@DisplayName("Un besoin Go publie son annonce")
class NeedAnnouncementLinkTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID announcementId = UUID.randomUUID();
    private final UUID freelancerId = UUID.randomUUID();
    private final UUID responseId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    /** gofp_freelancers.id of the responder (the response carries its core freelancer id). */
    private final UUID gofpFreelancerId = UUID.randomUUID();

    private final IDeliveryAnnouncementPort port = mock(IDeliveryAnnouncementPort.class);
    private final TenantContextHolder tenant = mock(TenantContextHolder.class);
    private final IDeliveryNeedRepository needs = mock(IDeliveryNeedRepository.class);
    private final AddressUseCase addressUseCase = mock(AddressUseCase.class);
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final DeliveryOtpService deliveryOtp = mock(DeliveryOtpService.class);

    /** Miroir local des annonces, en mémoire. */
    private final Map<UUID, Announcement> mirror = new HashMap<>();
    private final Map<UUID, DeliveryNeed> needStore = new HashMap<>();

    private DeliveryNeedApplicationService needService;
    private AnnouncementApplicationService announcementService;

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
        when(needs.deleteById(any(UUID.class))).thenReturn(Mono.empty());
        when(tenant.currentTenantId()).thenReturn(Mono.just(tenantId));

        NeedAnnouncementService link = new NeedAnnouncementService(port, tenant, mirrorRepo, needs, freelancers);

        GofpUserRepository users = mock(GofpUserRepository.class);
        when(users.findByCoreUserId(any())).thenReturn(Mono.empty());
        needService = new DeliveryNeedApplicationService(
                needs, mock(AdminRelayPointUseCase.class), mock(PushNotificationPort.class),
                mock(MatchingUseCase.class), addressUseCase, users,
                freelancers, mock(FreelancerPriceQuoteUseCase.class), mock(FreelancerEligibilityUseCase.class),
                legacyUserExists());
        needService.setNeedAnnouncements(link);

        announcementService = new AnnouncementApplicationService(
                port, tenant, mock(FreelancerQuotaService.class),
                mock(INegotiationChatPort.class), mirrorRepo, mock(AnnouncementSubscriptionRepository.class),
                freelancers, mock(GofpUserRepository.class), deliveries, deliveryOtp,
                mock(GofpDeliveryOtpProperties.class));
        announcementService.setNeedAnnouncements(link);

        when(addressUseCase.createAddress(any())).thenAnswer(inv -> {
            AddressDTO in = inv.getArgument(0);
            return Mono.just(AddressDTO.builder().id(UUID.randomUUID()).address(in.getAddress()).build());
        });
        when(addressUseCase.getAddressById(any())).thenReturn(Mono.empty());
        when(port.publish(any())).thenAnswer(inv -> {
            PublishAnnouncementPortCommand c = inv.getArgument(0);
            return Mono.just(snapshot(c.clientId(), c.pricingMode(), null, null, List.of()));
        });
        when(port.cancel(any(), any(), any())).thenReturn(Mono.empty());
        when(port.findById(eq(tenantId), eq(announcementId))).thenAnswer(inv -> Mono.just(
                snapshot(ownerId, "QUOTE_REQUEST", null, null, List.of(response()))));
        when(deliveries.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(deliveries.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(deliveryOtp.initOtpIfAbsentWithCodes(any())).thenReturn(Mono.empty());
        when(freelancers.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(freelancers.findByCoreFreelancerId(any())).thenReturn(Mono.empty());
    }

    /** Table historique {@code users} simulée : la lecture et l'INSERT idempotent réussissent. */
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

    private AnnouncementResponseSnapshot response() {
        return new AnnouncementResponseSnapshot(responseId, freelancerId, new BigDecimal("2500"), "XAF",
                "SENT", Instant.now());
    }

    private AnnouncementSnapshot snapshot(UUID clientId, String pricingMode, UUID selected, UUID createdDelivery,
                                          List<AnnouncementResponseSnapshot> responses) {
        return new AnnouncementSnapshot(
                announcementId, tenantId, clientId, "Colis", "desc",
                selected != null ? AnnouncementStatus.ASSIGNED : AnnouncementStatus.PUBLISHED,
                Instant.now(), Instant.now(), null, "XAF", pricingMode,
                pickupAddress(), dropAddress(), 0.0,
                null, null, null, null, null, null, null, null,
                "Destinataire", "+237600000000", selected, createdDelivery, null, responses);
    }

    private static Address pickupAddress() {
        return Address.builder().street("Rue Joss").city("Douala").country("CM")
                .coordinates(GeoCoordinates.of(4.0511, 9.7679)).build();
    }

    private static Address dropAddress() {
        return Address.builder().landmark("Carrefour Ndokoti").city("Douala").country("CM").build();
    }

    private DeliveryNeedRequestDTO request() {
        DeliveryNeedRequestDTO r = new DeliveryNeedRequestDTO();
        r.setTitle("Colis vêtements");
        r.setDescription("2 kg");
        r.setPaymentMethod("CASH");
        r.setPickupAddress(AddressDTO.builder().address(pickupAddress()).build());
        r.setDeliveryAddress(AddressDTO.builder().address(dropAddress()).build());
        return r;
    }

    private UUID createNeed() {
        return needService.createDeliveryNeed(request(), ownerId).block().getId();
    }

    // ── publication ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("créer un besoin publie UNE annonce QUOTE_REQUEST liée au besoin, au nom de l'expéditeur")
    void creatingNeed_publishesQuoteRequestAnnouncement() {
        var created = needService.createDeliveryNeed(request(), ownerId).block();

        ArgumentCaptor<PublishAnnouncementPortCommand> cmd = ArgumentCaptor.forClass(PublishAnnouncementPortCommand.class);
        verify(port, times(1)).publish(cmd.capture());
        assertThat(cmd.getValue().pricingMode()).isEqualTo("QUOTE_REQUEST");
        assertThat(cmd.getValue().clientId()).isEqualTo(ownerId);
        assertThat(cmd.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(cmd.getValue().pickupAddress().getCity()).isEqualTo("Douala");
        assertThat(cmd.getValue().pickupAddress().getCoordinates()).isNotNull();
        assertThat(cmd.getValue().deliveryAddress().getLandmark()).contains("Carrefour Ndokoti");

        Announcement local = mirror.get(announcementId);
        assertThat(local).as("miroir local de l'annonce").isNotNull();
        assertThat(local.getDeliveryNeedId()).isEqualTo(created.getId());
        assertThat(local.getStatus()).isEqualTo(AnnouncementStatus.PUBLISHED);
        assertThat(local.getPaymentMethod()).isEqualTo("CASH");
        assertThat(created.getAnnouncementId()).isEqualTo(announcementId);
    }

    @Test
    @DisplayName("la publication échoue → la création du besoin échoue (pas de besoin sans annonce)")
    void publishFailure_failsNeedCreation() {
        doReturn(Mono.error(new IllegalStateException("delivery-core down"))).when(port).publish(any());

        StepVerifier.create(needService.createDeliveryNeed(request(), ownerId))
                .expectErrorMessage("delivery-core down")
                .verify();
    }

    // ── assignation par l'annonce ───────────────────────────────────────────

    @Test
    @DisplayName("l'expéditeur retient une réponse → le besoin passe ASSIGNED au même livreur, livraison rattachée au besoin")
    void assigningViaAnnouncement_assignsTheNeed() {
        UUID needId = createNeed();
        when(port.selectResponse(tenantId, announcementId, ownerId, responseId)).thenReturn(Mono.just(
                snapshot(ownerId, "QUOTE_REQUEST", responseId, deliveryId, List.of(response()))));
        mirror.get(announcementId).setPaymentMethod("CASH");
        when(freelancers.findByCoreFreelancerId(freelancerId)).thenReturn(Mono.just(GofpFreelancer.builder()
                .id(gofpFreelancerId).coreFreelancerId(freelancerId).build()));

        StepVerifier.create(announcementService.assignResponse(announcementId, ownerId, responseId))
                .expectNextCount(1)
                .verifyComplete();

        DeliveryNeed need = needStore.get(needId);
        assertThat(need.getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);
        assertThat(need.getAssignedFreelancerId())
                .as("translated to gofp_freelancers.id (FK of delivery_needs.assigned_freelancer_id)")
                .isEqualTo(gofpFreelancerId);

        ArgumentCaptor<Delivery> mirrored = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveries).save(mirrored.capture());
        assertThat(mirrored.getValue().getDeliveryNeedId())
                .as("la livraison reste rattachée au besoin → le paiement lit le mode du besoin")
                .isEqualTo(needId);
        assertThat(mirrored.getValue().getTarif())
                .as("tarif = prix proposé retenu : c'est le montant que DeliveryPaymentApplicationService fera payer")
                .isEqualTo(2500.0);
    }

    @Test
    @DisplayName("répondant sans ligne gofp_freelancers → besoin ASSIGNED sans id (jamais une valeur hors clé étrangère)")
    void assigningViaAnnouncement_unknownGofpFreelancer_keepsFkClean() {
        UUID needId = createNeed();
        when(port.selectResponse(tenantId, announcementId, ownerId, responseId)).thenReturn(Mono.just(
                snapshot(ownerId, "QUOTE_REQUEST", responseId, deliveryId, List.of(response()))));

        StepVerifier.create(announcementService.assignResponse(announcementId, ownerId, responseId))
                .expectNextCount(1)
                .verifyComplete();

        DeliveryNeed need = needStore.get(needId);
        assertThat(need.getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);
        assertThat(need.getAssignedFreelancerId()).isNull();
    }

    // ── retrait de l'annonce ────────────────────────────────────────────────

    @Test
    @DisplayName("supprimer le besoin retire son annonce (plus PUBLISHED)")
    void deletingNeed_withdrawsAnnouncement() {
        UUID needId = createNeed();

        needService.deleteDeliveryNeed(needId, ownerId).block();

        verify(port).cancel(tenantId, announcementId, ownerId);
        assertThat(mirror.get(announcementId).getStatus()).isEqualTo(AnnouncementStatus.CANCELLED);
    }

    @Test
    @DisplayName("assigner le besoin directement (sans l'annonce) retire aussi l'annonce")
    void directAssignment_withdrawsAnnouncement() {
        UUID needId = createNeed();
        when(freelancers.findById(freelancerId)).thenReturn(Mono.just(GofpFreelancer.builder()
                .id(freelancerId).status(FreelancerStatus.APPROVED).isActive(true).build()));

        needService.assignFreelancer(needId, freelancerId, ownerId).block();

        verify(port).cancel(tenantId, announcementId, ownerId);
        assertThat(mirror.get(announcementId).getStatus()).isEqualTo(AnnouncementStatus.CANCELLED);
    }
}
