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

    private final FreelancerPriceQuoteUseCase quotes = mock(FreelancerPriceQuoteUseCase.class);

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
                freelancers, quotes, mock(FreelancerEligibilityUseCase.class),
                legacyUserExists());
        needService.setNeedAnnouncements(link);

        announcementService = new AnnouncementApplicationService(
                port, tenant, mock(FreelancerQuotaService.class),
                mock(INegotiationChatPort.class), mirrorRepo, mock(AnnouncementSubscriptionRepository.class),
                freelancers, mock(GofpUserRepository.class), deliveries, deliveryOtp,
                mock(GofpDeliveryOtpProperties.class));
        announcementService.setNeedAnnouncements(link);
        needService.setAnnouncements(announcementService);

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
    @DisplayName("C-56 — les contacts saisis sont enregistrés sur le besoin, publiés, et visibles du livreur ; jamais inventés")
    void needContacts_keptPublishedAndMirrored() {
        DeliveryNeedRequestDTO r = request();
        r.setSenderFirstName("  Awa Expéditrice ");
        r.setSenderPhone("+237690000416");
        r.setRecipientFirstName("Marie");
        r.setRecipientLastName("Destinataire");
        r.setRecipientPhone("+237677000001");

        var created = needService.createDeliveryNeed(r, ownerId).block();

        ArgumentCaptor<PublishAnnouncementPortCommand> cmd = ArgumentCaptor.forClass(PublishAnnouncementPortCommand.class);
        verify(port).publish(cmd.capture());
        assertThat(cmd.getValue().recipientName()).isEqualTo("Marie Destinataire");
        assertThat(cmd.getValue().recipientPhone()).isEqualTo("+237677000001");
        DeliveryNeed saved = needStore.get(created.getId());
        assertThat(saved.getSenderFirstName()).isEqualTo("Awa Expéditrice");
        assertThat(saved.getRecipientPhone()).isEqualTo("+237677000001");
        assertThat(created.getSenderPhone()).as("relu par l'expéditeur").isEqualTo("+237690000416");
        Announcement local = mirror.get(announcementId);
        assertThat(local.getShipperFirstName()).as("vu par le livreur").isEqualTo("Awa Expéditrice");
        assertThat(local.getRecipientPhone()).isEqualTo("+237677000001");
    }

    @Test
    @DisplayName("C-56 — contacts absents : « — », jamais « Recipient » ni +237000000000")
    void needWithoutContacts_neverInventsOne() {
        needService.createDeliveryNeed(request(), ownerId).block();

        ArgumentCaptor<PublishAnnouncementPortCommand> cmd = ArgumentCaptor.forClass(PublishAnnouncementPortCommand.class);
        verify(port).publish(cmd.capture());
        assertThat(cmd.getValue().recipientName()).isEqualTo("—");
        assertThat(cmd.getValue().recipientPhone()).isEqualTo("—");
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

    // ── assignation directe (lot C-46) : elle passe par l'annonce → livraison payable ─────────

    private GofpFreelancer assignableCourier() {
        GofpFreelancer f = GofpFreelancer.builder().id(gofpFreelancerId).coreFreelancerId(freelancerId)
                .status(FreelancerStatus.APPROVED).isActive(true).build();
        when(freelancers.findById(gofpFreelancerId)).thenReturn(Mono.just(f));
        when(freelancers.findByCoreFreelancerId(freelancerId)).thenReturn(Mono.just(f));
        return f;
    }

    private AnnouncementResponseSnapshot responseAt(String price) {
        return new AnnouncementResponseSnapshot(responseId, freelancerId, new BigDecimal(price), "XAF",
                "SENT", Instant.now());
    }

    /** L'annonce n'a encore aucune réponse ; respond l'ajoute ; selectResponse crée la livraison. */
    private void openAnnouncementWithoutResponse() {
        java.util.concurrent.atomic.AtomicBoolean responded = new java.util.concurrent.atomic.AtomicBoolean();
        when(port.findById(eq(tenantId), eq(announcementId))).thenAnswer(inv -> Mono.just(
                snapshot(ownerId, "QUOTE_REQUEST", null, null,
                        responded.get() ? List.of(responseAt("3000")) : List.of())));
        when(port.respond(any())).thenAnswer(inv -> {
            responded.set(true);
            return Mono.just(snapshot(ownerId, "QUOTE_REQUEST", null, null, List.of(responseAt("3000"))));
        });
        when(port.selectResponse(tenantId, announcementId, ownerId, responseId)).thenReturn(Mono.just(
                snapshot(ownerId, "QUOTE_REQUEST", responseId, deliveryId, List.of(responseAt("3000")))));
    }

    private void serverQuote(double price) {
        when(quotes.quote(any(), any(), any())).thenReturn(Mono.just(
                new FreelancerPriceQuoteUseCase.Quote(price, "XAF", "grille", "TEST", null)));
    }

    /**
     * Lot C-56 — le livreur qui accepte une invitation porte SON `tid` ; l'annonce vit dans le
     * tenant de l'expéditeur. Le tenant simulé lit la surcharge posée par {@link TenantContextHolder#underTenant}.
     */
    private void callerInTenant(UUID callerTenant) {
        when(tenant.currentTenantId()).thenReturn(Mono.deferContextual(view -> Mono.just(
                view.hasKey(TenantContextHolder.TENANT_OVERRIDE)
                        ? view.<UUID>get(TenantContextHolder.TENANT_OVERRIDE) : callerTenant)));
    }

    @Test
    @DisplayName("C-56 — un livreur d'un AUTRE tenant accepte : l'élection lit l'annonce dans le tenant de l'annonce (plus de 404)")
    void electionByCourierOfAnotherTenant_usesAnnouncementTenant() {
        UUID needId = createNeed();
        GofpFreelancer courier = assignableCourier();
        openAnnouncementWithoutResponse();
        serverQuote(3000);
        UUID courierTenant = UUID.randomUUID();
        when(port.findById(eq(courierTenant), any())).thenReturn(Mono.empty());
        when(port.tenantOf(announcementId)).thenReturn(Mono.just(tenantId));
        callerInTenant(courierTenant);

        StepVerifier.create(needService.completeAssignment(needStore.get(needId), courier))
                .assertNext(dto -> assertThat(dto.getId()).isEqualTo(needId))
                .verifyComplete();

        ArgumentCaptor<com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.RespondToAnnouncementPortCommand> respond =
                ArgumentCaptor.forClass(com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.RespondToAnnouncementPortCommand.class);
        verify(port).respond(respond.capture());
        assertThat(respond.getValue().tenantId()).as("réponse écrite dans le tenant de l'annonce").isEqualTo(tenantId);
        verify(port).selectResponse(tenantId, announcementId, ownerId, responseId);
        assertThat(needStore.get(needId).getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);
    }

    @Test
    @DisplayName("assigner directement un livreur crée la livraison payable, rattachée au besoin, au prix calculé par le serveur")
    void directAssignment_createsPayableDeliveryPricedByServer() {
        UUID needId = createNeed();
        assignableCourier();
        openAnnouncementWithoutResponse();
        serverQuote(3000);

        needService.assignFreelancer(needId, gofpFreelancerId, ownerId, true).block();

        ArgumentCaptor<com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.RespondToAnnouncementPortCommand> respond =
                ArgumentCaptor.forClass(com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.RespondToAnnouncementPortCommand.class);
        verify(port).respond(respond.capture());
        assertThat(respond.getValue().freelancerId()).as("réponse au nom du livreur (id profil)").isEqualTo(freelancerId);
        assertThat(respond.getValue().proposedPrice()).as("prix = devis du serveur").isEqualByComparingTo("3000");
        verify(port).selectResponse(tenantId, announcementId, ownerId, responseId);

        ArgumentCaptor<Delivery> created = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveries).save(created.capture());
        assertThat(created.getValue().getDeliveryNeedId()).isEqualTo(needId);
        assertThat(created.getValue().getTarif()).isEqualTo(3000.0);
        assertThat(needStore.get(needId).getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);
        assertThat(needStore.get(needId).getAssignedFreelancerId()).isEqualTo(gofpFreelancerId);
        verify(port, never()).cancel(any(), any(), any());
    }

    @Test
    @DisplayName("rejouer l'assignation directe ne crée pas une seconde livraison")
    void directAssignment_replayed_noDuplicateDelivery() {
        UUID needId = createNeed();
        assignableCourier();
        openAnnouncementWithoutResponse();
        serverQuote(3000);

        needService.assignFreelancer(needId, gofpFreelancerId, ownerId, true).block();
        needService.assignFreelancer(needId, gofpFreelancerId, ownerId, true).block();

        verify(port, times(1)).selectResponse(any(), any(), any(), any());
        verify(deliveries, times(1)).save(any());
    }

    @Test
    @DisplayName("devis indisponible → assignation refusée, aucun tarif à 0, rien n'est créé")
    void directAssignment_quoteUnavailable_refused() {
        UUID needId = createNeed();
        assignableCourier();
        openAnnouncementWithoutResponse();
        when(quotes.quote(any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("no pricing")));

        StepVerifier.create(needService.assignFreelancer(needId, gofpFreelancerId, ownerId, true))
                .expectErrorMatches(e -> e instanceof org.springframework.web.server.ResponseStatusException r
                        && r.getStatusCode().value() == 409)
                .verify();
        verify(port, never()).respond(any());
        verify(deliveries, never()).save(any());
        assertThat(needStore.get(needId).getStatus()).isNotEqualTo(DeliveryNeedStatus.ASSIGNED);
    }

    @Test
    @DisplayName("le livreur a déjà proposé un autre prix → refus : le tarif ne vient jamais d'une requête")
    void directAssignment_existingResponseAtOtherPrice_refused() {
        UUID needId = createNeed();
        assignableCourier();
        when(port.findById(eq(tenantId), eq(announcementId))).thenAnswer(inv -> Mono.just(
                snapshot(ownerId, "QUOTE_REQUEST", null, null, List.of(responseAt("500")))));
        serverQuote(3000);

        StepVerifier.create(needService.assignFreelancer(needId, gofpFreelancerId, ownerId))
                .expectErrorMatches(e -> e instanceof org.springframework.web.server.ResponseStatusException r
                        && r.getStatusCode().value() == 409)
                .verify();
        verify(port, never()).selectResponse(any(), any(), any(), any());
        verify(deliveries, never()).save(any());
    }

    // ── PUT d'annonce (lot C-46) ────────────────────────────────────────────

    @Test
    @DisplayName("annonce attribuée : son mode de paiement et son prix ne changent plus (409) ; avant, ils changent")
    void assignedAnnouncement_paymentFrozen() {
        createNeed();
        Announcement local = mirror.get(announcementId);
        var toCash = new com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO();
        toCash.setPaymentMethod("WALLET");

        StepVerifier.create(announcementService.updateAnnouncement(announcementId, toCash)).expectNextCount(1).verifyComplete();
        assertThat(local.getPaymentMethod()).isEqualTo("WALLET");

        local.setStatus(AnnouncementStatus.ASSIGNED);
        local.setAssignedFreelancerId(gofpFreelancerId);
        toCash.setPaymentMethod("CASH");
        StepVerifier.create(announcementService.updateAnnouncement(announcementId, toCash))
                .expectErrorMatches(e -> e instanceof org.springframework.web.server.ResponseStatusException r
                        && r.getStatusCode().value() == 409)
                .verify();
        assertThat(local.getPaymentMethod()).isEqualTo("WALLET");
    }

    // ── lot C-52 : invitation au lieu d'assignation forcée ──────────────────

    @Test
    @DisplayName("C-52 : l'expéditeur ne peut plus assigner un livreur qui n'a pas postulé → 409, rien n'est créé")
    void clientAssign_nonApplicant_refused() {
        UUID needId = createNeed();
        assignableCourier();
        openAnnouncementWithoutResponse();
        serverQuote(3000);

        StepVerifier.create(needService.assignFreelancer(needId, gofpFreelancerId, ownerId, false))
                .expectErrorMatches(e -> e instanceof org.springframework.web.server.ResponseStatusException r
                        && r.getStatusCode().value() == 409)
                .verify();
        verify(port, never()).respond(any());
        verify(port, never()).selectResponse(any(), any(), any(), any());
        verify(deliveries, never()).save(any());
        assertThat(needStore.get(needId).getStatus()).isNotEqualTo(DeliveryNeedStatus.ASSIGNED);
    }

    @Test
    @DisplayName("C-52 : l'expéditeur élit un livreur qui a postulé (au prix serveur) → livraison créée")
    void clientAssign_applicant_ok() {
        UUID needId = createNeed();
        assignableCourier();
        when(port.findById(eq(tenantId), eq(announcementId))).thenAnswer(inv -> Mono.just(
                snapshot(ownerId, "QUOTE_REQUEST", null, null, List.of(responseAt("3000")))));
        when(port.selectResponse(tenantId, announcementId, ownerId, responseId)).thenReturn(Mono.just(
                snapshot(ownerId, "QUOTE_REQUEST", responseId, deliveryId, List.of(responseAt("3000")))));
        serverQuote(3000);

        needService.assignFreelancer(needId, gofpFreelancerId, ownerId, false).block();

        verify(port).selectResponse(tenantId, announcementId, ownerId, responseId);
        assertThat(needStore.get(needId).getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);
    }

    private final UUID courierUserId = UUID.randomUUID();

    private NeedInvitationService invitationService(Map<UUID, com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation> store) {
        NeedInvitationRepository repo = new NeedInvitationRepository() {
            @Override public Mono<com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation> save(
                    com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation i) {
                store.put(i.getId(), i);
                return Mono.just(i);
            }
            @Override public Mono<com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation> findById(UUID id) {
                return Mono.justOrEmpty(store.get(id));
            }
            @Override public reactor.core.publisher.Flux<com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation> findAllByDeliveryNeedId(UUID n) {
                return reactor.core.publisher.Flux.fromIterable(store.values()).filter(i -> n.equals(i.getDeliveryNeedId()));
            }
            @Override public reactor.core.publisher.Flux<com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation> findAllByFreelancerIdAndStatus(UUID f, String st) {
                return reactor.core.publisher.Flux.fromIterable(store.values())
                        .filter(i -> f.equals(i.getFreelancerId()) && st.equals(i.getStatus()));
            }
        };
        return new NeedInvitationService(needs, freelancers, repo, needService);
    }

    private UUID openInvitation(Map<UUID, com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation> store, UUID needId) {
        UUID id = UUID.randomUUID();
        store.put(id, com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation.builder()
                .id(id).deliveryNeedId(needId).freelancerId(gofpFreelancerId).invitedBy(ownerId)
                .status("OPEN").expiresAt(Instant.now().plusSeconds(600)).createdAt(Instant.now()).build());
        return id;
    }

    @Test
    @DisplayName("C-52 : invitation acceptée → même livraison payable qu'une assignation C-46 (besoin lié, prix serveur, mode du besoin)")
    void invitationAccepted_createsPayableDelivery() {
        UUID needId = createNeed();
        GofpFreelancer courier = assignableCourier();
        courier.setCoreUserId(courierUserId);
        when(freelancers.findByCoreUserId(courierUserId)).thenReturn(Mono.just(courier));
        openAnnouncementWithoutResponse();
        serverQuote(3000);
        Map<UUID, com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation> store = new HashMap<>();
        UUID invId = openInvitation(store, needId);

        invitationService(store).accept(invId, courierUserId).block();

        verify(port).selectResponse(tenantId, announcementId, ownerId, responseId);
        ArgumentCaptor<Delivery> created = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveries).save(created.capture());
        assertThat(created.getValue().getDeliveryNeedId())
                .as("livraison rattachée au besoin → le paiement (portefeuille ou espèces) lit le mode du besoin")
                .isEqualTo(needId);
        assertThat(created.getValue().getTarif()).isEqualTo(3000.0);
        assertThat(needStore.get(needId).getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);
        assertThat(needStore.get(needId).getPaymentMethod()).isEqualTo("CASH");
        assertThat(store.get(invId).getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("C-52 : invitation refusée → l'annonce reste publiée, le besoin reste en attente")
    void invitationDeclined_announcementStaysPublished() {
        UUID needId = createNeed();
        GofpFreelancer courier = assignableCourier();
        courier.setCoreUserId(courierUserId);
        when(freelancers.findByCoreUserId(courierUserId)).thenReturn(Mono.just(courier));
        Map<UUID, com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation> store = new HashMap<>();
        UUID invId = openInvitation(store, needId);

        invitationService(store).decline(invId, courierUserId, "pas dispo").block();

        assertThat(store.get(invId).getStatus()).isEqualTo("DECLINED");
        assertThat(mirror.get(announcementId).getStatus()).isEqualTo(AnnouncementStatus.PUBLISHED);
        verify(port, never()).cancel(any(), any(), any());
        verify(port, never()).selectResponse(any(), any(), any(), any());
        assertThat(needStore.get(needId).getStatus()).isEqualTo(DeliveryNeedStatus.PENDING);
    }

    @Test
    @DisplayName("C-53 : l'élection crée les codes de remise de la livraison (pas le premier appui du livreur)")
    void election_createsHandoverCodes() {
        UUID needId = createNeed();
        assignableCourier();
        openAnnouncementWithoutResponse();
        serverQuote(3000);
        Delivery created = new Delivery();
        created.setId(deliveryId);
        created.setDeliveryNeedId(needId);
        when(deliveries.findByDeliveryNeedId(needId)).thenReturn(Mono.just(created));
        when(deliveryOtp.initOtpIfAbsent(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        needService.setDeliveries(deliveries);
        needService.setDeliveryOtp(deliveryOtp);

        needService.assignFreelancer(needId, gofpFreelancerId, ownerId, true).block();

        verify(deliveryOtp).initOtpIfAbsent(created);
    }
}
