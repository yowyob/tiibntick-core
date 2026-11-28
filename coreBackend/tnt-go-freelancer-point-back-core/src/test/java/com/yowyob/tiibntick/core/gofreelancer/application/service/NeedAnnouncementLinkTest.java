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
import com.yowyob.tiibntick.core.gofreelancer.testsupport.InMemoryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementExtras;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpDeliveryOtpProperties;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus;
import com.yowyob.tiibntick.core.geo.domain.model.HubStatus;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * {@link NeedAnnouncementService} et {@link DeliveryNeedStateService} ; tnt-delivery-core est un
 * port en mémoire ({@link InMemoryAnnouncementPort}) : l'annonce du core est le seul état, le lien
 * besoin → annonce est sa référence d'origine, et le statut du besoin en est dérivé.
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

    private InMemoryAnnouncementPort port;
    private final TenantContextHolder tenant = mock(TenantContextHolder.class);
    private final IDeliveryNeedRepository needs = mock(IDeliveryNeedRepository.class);
    private final AddressUseCase addressUseCase = mock(AddressUseCase.class);
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort lifecycle =
            mock(com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.class);
    private final DeliveryOtpService deliveryOtp = mock(DeliveryOtpService.class);

    private final Map<UUID, DeliveryNeed> needStore = new HashMap<>();
    private DeliveryNeedStateService state;

    private final FreelancerPriceQuoteUseCase quotes = mock(FreelancerPriceQuoteUseCase.class);

    private DeliveryNeedApplicationService needService;
    private AnnouncementApplicationService announcementService;

    @BeforeEach
    void setUp() {
        port = spy(new InMemoryAnnouncementPort().nextAnnouncementId(announcementId).nextDeliveryId(deliveryId)
                .nextResponseId(responseId));
        when(lifecycle.findState(any(), any())).thenReturn(Mono.empty());
        state = new DeliveryNeedStateService(port, lifecycle, tenant);

        when(needs.save(any())).thenAnswer(inv -> {
            DeliveryNeed n = inv.getArgument(0);
            needStore.put(n.getId(), n);
            return state.hydrate(n);
        });
        when(needs.findById(any(UUID.class))).thenAnswer(inv -> Mono.justOrEmpty(needStore.get(inv.getArgument(0)))
                .flatMap(state::hydrate));
        when(needs.deleteById(any(UUID.class))).thenReturn(Mono.empty());
        when(tenant.currentTenantId()).thenReturn(Mono.just(tenantId));

        NeedAnnouncementService link = new NeedAnnouncementService(port, tenant);

        GofpUserRepository users = mock(GofpUserRepository.class);
        when(users.findByCoreUserId(any())).thenReturn(Mono.empty());
        needService = new DeliveryNeedApplicationService(
                needs, mock(AdminRelayPointUseCase.class), mock(PushNotificationPort.class),
                mock(MatchingUseCase.class), addressUseCase, users,
                freelancers, quotes, mock(FreelancerEligibilityUseCase.class));
        needService.setNeedAnnouncements(link);

        announcementService = new AnnouncementApplicationService(
                port, tenant, mock(FreelancerQuotaService.class),
                mock(INegotiationChatPort.class),
                freelancers, mock(GofpUserRepository.class), deliveries, deliveryOtp,
                mock(GofpDeliveryOtpProperties.class), lifecycle);
        // The agreed price and windows are recorded on the core delivery (lot L1).
        com.yowyob.tiibntick.core.gofreelancer.TestDeliveries.acceptTerms(lifecycle);
        when(lifecycle.recordAgreedTerms(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> Mono.just(new com.yowyob.tiibntick.core.gofreelancer.application.port.out
                        .IDeliveryLifecyclePort.CoreDeliveryState(inv.getArgument(1), "CREATED", null, null, null, null)));
        needService.setAnnouncements(announcementService);

        when(addressUseCase.createAddress(any())).thenAnswer(inv -> {
            AddressDTO in = inv.getArgument(0);
            return Mono.just(AddressDTO.builder().id(UUID.randomUUID()).address(in.getAddress()).build());
        });
        when(addressUseCase.getAddressById(any())).thenReturn(Mono.empty());
        when(deliveries.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(deliveries.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(deliveryOtp.initOtpIfAbsentWithCodes(any())).thenReturn(Mono.empty());
        when(freelancers.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(freelancers.findByCoreFreelancerId(any())).thenReturn(Mono.empty());
    }

    private AnnouncementResponseSnapshot response() {
        return new AnnouncementResponseSnapshot(responseId, freelancerId, new BigDecimal("2500"), "XAF",
                "SENT", Instant.now());
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

        AnnouncementSnapshot published = port.get(announcementId);
        assertThat(published.extras().originRefType()).isEqualTo(AnnouncementExtras.ORIGIN_DELIVERY_NEED);
        assertThat(published.extras().originRefId()).as("référence d'origine = le besoin").isEqualTo(created.getId());
        assertThat(published.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
        assertThat(published.extras().paymentMethod()).isEqualTo("CASH");
        assertThat(created.getAnnouncementId()).isEqualTo(announcementId);
        assertThat(needStore.get(created.getId()).getAnnouncementId()).as("pointeur dénormalisé").isEqualTo(announcementId);
        assertThat(created.getStatus()).isEqualTo(DeliveryNeedStatus.PENDING);
    }

    // ── destination relais (D-140, D-144) ──────────────────────────────────

    private final GofpRelayPointRepository targetRelays = mock(GofpRelayPointRepository.class);
    private final IRelayHubPort targetHubs = mock(IRelayHubPort.class);

    private UUID wireTarget(RelayPointStatus relayStatus, boolean active, HubApprovalStatus approval, HubStatus hubStatus) {
        return wireTarget(relayStatus, active, approval, hubStatus, 0);
    }

    private UUID wireTarget(RelayPointStatus relayStatus, boolean active, HubApprovalStatus approval, HubStatus hubStatus,
                            int occupancy) {
        UUID hubId = UUID.randomUUID();
        when(targetRelays.findById(any())).thenReturn(Mono.empty());
        when(targetRelays.findByCoreRelayPointId(any())).thenReturn(Mono.empty());
        when(targetHubs.findHub(any(), any())).thenReturn(Mono.error(new GeoNotFoundException("RelayHub", "x")));
        if (relayStatus != null) {
            GofpRelayPoint rp = GofpRelayPoint.builder().id(UUID.randomUUID()).coreRelayPointId(hubId)
                    .status(relayStatus).isActive(active).build();
            when(targetRelays.findById(rp.getId())).thenReturn(Mono.just(rp));
            when(targetRelays.findByCoreRelayPointId(hubId)).thenReturn(Mono.just(rp));
        }
        if (approval != null) {
            Instant t = Instant.now();
            when(targetHubs.findHub(eq(hubId), any())).thenReturn(Mono.just(RelayHub.rehydrate(hubId, tenantId, null,
                    RoadNodeId.generate(), 4, occupancy, "op", hubStatus, approval, t, t, null, null, null)));
        }
        needService.setRelayPoints(targetRelays);
        needService.setRelayHubs(targetHubs);
        return hubId;
    }

    private void expectStatus(UUID target, int status) {
        var r = request();
        r.setTargetRelayPointId(target);
        StepVerifier.create(needService.createDeliveryNeed(r, ownerId))
                .expectErrorMatches(e -> e instanceof ResponseStatusException rse && rse.getStatusCode().value() == status)
                .verify();
        verify(port, never()).publish(any());
        assertThat(needStore).isEmpty();
    }

    @ParameterizedTest(name = "relais Go {0} → 409, rien de créé")
    @EnumSource(value = RelayPointStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("D-140 : un relais Go non agréé n'est pas une destination")
    void notApprovedRelayIsNoDestination(RelayPointStatus status) {
        UUID hubId = wireTarget(status, true, HubApprovalStatus.APPROVED, HubStatus.ACTIVE);
        expectStatus(hubId, 409);
    }

    @ParameterizedTest(name = "hub du cœur {0} → 409")
    @EnumSource(value = HubApprovalStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("D-144 : le registre du cœur fait foi, un hub non agréé est refusé même si le relais Go se dit agréé")
    void notApprovedCoreHubIsNoDestination(HubApprovalStatus approval) {
        UUID hubId = wireTarget(RelayPointStatus.APPROVED, true, approval, HubStatus.ACTIVE);
        expectStatus(hubId, 409);
    }

    @ParameterizedTest(name = "hub {0} → 409")
    @EnumSource(value = HubStatus.class, names = {"TEMPORARILY_CLOSED", "PERMANENTLY_CLOSED"})
    @DisplayName("D-144 : un hub fermé n'est pas une destination")
    void closedHubIsNoDestination(HubStatus closed) {
        UUID hubId = wireTarget(RelayPointStatus.APPROVED, true, HubApprovalStatus.APPROVED, closed);
        expectStatus(hubId, 409);
    }

    @Test
    @DisplayName("D-144 : destination inconnue du registre → 404 (cible inconnue, ou relais Go sans hub du cœur)")
    void unknownDestinationIs404() {
        wireTarget(null, true, null, null);
        expectStatus(UUID.randomUUID(), 404);
        UUID relayHub = wireTarget(RelayPointStatus.APPROVED, true, null, null);
        expectStatus(relayHub, 404);
    }

    @Test
    @DisplayName("D-140 : un relais en pause (fermé par son propriétaire) → 409")
    void pausedRelayIsNoDestination() {
        UUID hubId = wireTarget(RelayPointStatus.APPROVED, false, HubApprovalStatus.APPROVED, HubStatus.ACTIVE);
        expectStatus(hubId, 409);
    }

    @Test
    @DisplayName("D-146 : un relais plein (statut FULL, ou plus aucune place) n'est pas une destination → 409")
    void fullHubIsNoDestination() {
        UUID full = wireTarget(RelayPointStatus.APPROVED, true, HubApprovalStatus.APPROVED, HubStatus.FULL, 4);
        expectStatus(full, 409);
        UUID noSlotLeft = wireTarget(RelayPointStatus.APPROVED, true, HubApprovalStatus.APPROVED, HubStatus.ACTIVE, 4);
        expectStatus(noSlotLeft, 409);
        UUID lastSlot = wireTarget(RelayPointStatus.APPROVED, true, HubApprovalStatus.APPROVED, HubStatus.ACTIVE, 3);
        StepVerifier.create(needService.requireProposableTarget(lastSlot)).as("une place libre suffit").verifyComplete();
    }

    @Test
    @DisplayName("D-144 : hub agréé et ouvert, désigné par le relais Go ou directement : destination acceptée")
    void approvedDestinationsAreAccepted() {
        UUID hubId = wireTarget(RelayPointStatus.APPROVED, true, HubApprovalStatus.APPROVED, HubStatus.ACTIVE);
        var r = request();
        r.setTargetRelayPointId(hubId);
        var created = needService.createDeliveryNeed(r, ownerId).block();
        assertThat(needStore.get(created.getId()).getTargetRelayPointId()).isEqualTo(hubId);

        UUID bareHub = wireTarget(null, true, HubApprovalStatus.APPROVED, HubStatus.ACTIVE);
        StepVerifier.create(needService.requireProposableTarget(bareHub)).verifyComplete();
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
        // The courier reads the core announcement (no local mirror any more): the sender contact travels in it.
        assertThat(cmd.getValue().extras().senderName()).as("vu par le livreur").isEqualTo("Awa Expéditrice");
        assertThat(cmd.getValue().extras().senderPhone()).isEqualTo("+237690000416");
    }

    @Test
    @DisplayName("C-56 — contacts absents : « — », jamais « Recipient » ni +237000000000")
    void needWithoutContacts_neverInventsOne() {
        needService.createDeliveryNeed(request(), ownerId).block();

        ArgumentCaptor<PublishAnnouncementPortCommand> cmd = ArgumentCaptor.forClass(PublishAnnouncementPortCommand.class);
        verify(port).publish(cmd.capture());
        assertThat(cmd.getValue().recipientName()).isEqualTo("—");
        assertThat(cmd.getValue().recipientPhone()).isEqualTo("—");
        assertThat(cmd.getValue().extras().senderName()).as("no sender contact invented").isNull();
        assertThat(cmd.getValue().extras().senderPhone()).isNull();
    }

    @Test
    @DisplayName("la publication échoue → la création du besoin échoue (pas de besoin sans annonce)")
    void publishFailure_failsNeedCreation() {
        doReturn(Mono.error(new IllegalStateException("delivery-core down"))).when(port).publish(any());

        StepVerifier.create(needService.createDeliveryNeed(request(), ownerId))
                .expectErrorMessage("delivery-core down")
                .verify();
    }

    @Test
    @DisplayName("L2 : republier un besoin déjà publié rend son annonce (aucune seconde publication, pas de 500)")
    void republishing_isIdempotent() {
        var created = needService.createDeliveryNeed(request(), ownerId).block();
        NeedAnnouncementService link = new NeedAnnouncementService(port, tenant);

        StepVerifier.create(link.publishFor(needStore.get(created.getId()), pickupAddress(), dropAddress(), null))
                .expectNext(announcementId)
                .verifyComplete();
        verify(port, times(1)).publish(any());
        assertThat(port.all()).hasSize(1);
    }

    @Test
    @DisplayName("L2 : publication concurrente perdue sur l'index unique → l'annonce gagnante est relue")
    void concurrentPublication_lostOnUniqueIndex_readsTheWinner() {
        var created = needService.createDeliveryNeed(request(), ownerId).block();
        // The concurrent request did not see the announcement yet; the core refuses the duplicate.
        doReturn(Mono.empty()).doCallRealMethod().when(port).findByOrigin(any(), any(), any());
        NeedAnnouncementService link = new NeedAnnouncementService(port, tenant);

        StepVerifier.create(link.publishFor(needStore.get(created.getId()), pickupAddress(), dropAddress(), null))
                .expectNext(announcementId)
                .verifyComplete();
        verify(port, times(2)).publish(any());
        assertThat(port.all()).hasSize(1);
    }

    @Test
    @DisplayName("L2 : autre échec de publication → l'erreur remonte telle quelle")
    void otherPublicationFailure_propagates() {
        var created = needService.createDeliveryNeed(request(), ownerId).block();
        doReturn(Mono.empty()).when(port).findByOrigin(any(), any(), any());
        doReturn(Mono.error(new IllegalStateException("delivery-core down"))).when(port).publish(any());
        NeedAnnouncementService link = new NeedAnnouncementService(port, tenant);

        StepVerifier.create(link.publishFor(needStore.get(created.getId()), pickupAddress(), dropAddress(), null))
                .expectErrorMessage("delivery-core down")
                .verify();
    }

    // ── assignation par l'annonce ───────────────────────────────────────────

    @Test
    @DisplayName("l'expéditeur retient une réponse → le besoin passe ASSIGNED au même livreur, livraison rattachée au besoin")
    void assigningViaAnnouncement_assignsTheNeed() {
        UUID needId = createNeed();
        port.addResponse(announcementId, response());

        StepVerifier.create(announcementService.assignResponse(announcementId, ownerId, responseId))
                .expectNextCount(1)
                .verifyComplete();

        DeliveryNeed need = needs.findById(needId).block();
        assertThat(need.getStatus()).as("dérivé : annonce ASSIGNED, livraison inconnue").isEqualTo(DeliveryNeedStatus.ASSIGNED);
        assertThat(need.getAssignedFreelancerId())
                .as("dérivé : id core du livreur dont la réponse est retenue")
                .isEqualTo(freelancerId);
        assertThat(need.getDeliveryId()).isEqualTo(deliveryId);

        ArgumentCaptor<Delivery> mirrored = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveries).save(mirrored.capture());
        assertThat(mirrored.getValue().getDeliveryNeedId())
                .as("la livraison reste rattachée au besoin → le paiement lit le mode du besoin")
                .isEqualTo(needId);
        assertThat(agreedPrice())
                .as("tarif = prix proposé retenu, enregistré sur la livraison du core : c'est le montant que DeliveryPaymentApplicationService fera payer")
                .isEqualByComparingTo("2500");
    }

    @Test
    @DisplayName("aucun statut n'est écrit sur le besoin : l'élection ne réenregistre pas le besoin")
    void assigningViaAnnouncement_writesNothingOnTheNeed() {
        UUID needId = createNeed();
        port.addResponse(announcementId, response());
        clearInvocations(needs);

        StepVerifier.create(announcementService.assignResponse(announcementId, ownerId, responseId))
                .expectNextCount(1)
                .verifyComplete();

        verify(needs, never()).save(any());
        assertThat(needs.findById(needId).block().getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);
    }

    // ── retrait de l'annonce ────────────────────────────────────────────────

    @Test
    @DisplayName("supprimer le besoin retire son annonce (plus PUBLISHED)")
    void deletingNeed_withdrawsAnnouncement() {
        UUID needId = createNeed();

        needService.deleteDeliveryNeed(needId, ownerId).block();

        verify(port).cancel(tenantId, announcementId, ownerId);
        assertThat(port.get(announcementId).status()).isEqualTo(AnnouncementStatus.CANCELLED);
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
        assertThat(port.get(announcementId).responses()).isEmpty();
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
        assertThat(agreedPrice()).isEqualByComparingTo("3000");
        DeliveryNeed need = needs.findById(needId).block();
        assertThat(need.getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);
        assertThat(need.getAssignedFreelancerId()).as("id core du livreur").isEqualTo(freelancerId);
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
        assertThat(needs.findById(needId).block().getStatus()).isNotEqualTo(DeliveryNeedStatus.ASSIGNED);
    }

    @Test
    @DisplayName("le livreur a déjà proposé un autre prix → refus : le tarif ne vient jamais d'une requête")
    void directAssignment_existingResponseAtOtherPrice_refused() {
        UUID needId = createNeed();
        assignableCourier();
        port.addResponse(announcementId, responseAt("500"));
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
        var toCash = new com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO();
        toCash.setPaymentMethod("WALLET");

        StepVerifier.create(announcementService.updateAnnouncement(announcementId, toCash)).expectNextCount(1).verifyComplete();
        assertThat(port.get(announcementId).extras().paymentMethod()).isEqualTo("WALLET");

        port.addResponse(announcementId, response());
        port.selectResponse(tenantId, announcementId, ownerId, responseId).block();
        toCash.setPaymentMethod("CASH");
        StepVerifier.create(announcementService.updateAnnouncement(announcementId, toCash))
                .expectErrorMatches(e -> e instanceof org.springframework.web.server.ResponseStatusException r
                        && r.getStatusCode().value() == 409)
                .verify();
        assertThat(port.get(announcementId).extras().paymentMethod()).isEqualTo("WALLET");
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
        assertThat(needs.findById(needId).block().getStatus()).isNotEqualTo(DeliveryNeedStatus.ASSIGNED);
    }

    @Test
    @DisplayName("C-52 : l'expéditeur élit un livreur qui a postulé (au prix serveur) → livraison créée")
    void clientAssign_applicant_ok() {
        UUID needId = createNeed();
        assignableCourier();
        port.addResponse(announcementId, responseAt("3000"));
        serverQuote(3000);

        needService.assignFreelancer(needId, gofpFreelancerId, ownerId, false).block();

        verify(port).selectResponse(tenantId, announcementId, ownerId, responseId);
        assertThat(needs.findById(needId).block().getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);
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
        assertThat(agreedPrice()).isEqualByComparingTo("3000");
        assertThat(needs.findById(needId).block().getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);
        assertThat(needStore.get(needId).getPaymentMethod()).isEqualTo("CASH");
        assertThat(port.get(announcementId).extras().paymentMethod()).isEqualTo("CASH");
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
        assertThat(port.get(announcementId).status()).isEqualTo(AnnouncementStatus.PUBLISHED);
        verify(port, never()).cancel(any(), any(), any());
        verify(port, never()).selectResponse(any(), any(), any(), any());
        assertThat(needs.findById(needId).block().getStatus()).isEqualTo(DeliveryNeedStatus.PENDING);
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

    /** The price recorded on the core delivery when the response was selected. */
    private java.math.BigDecimal agreedPrice() {
        ArgumentCaptor<java.math.BigDecimal> price = ArgumentCaptor.forClass(java.math.BigDecimal.class);
        verify(lifecycle).recordAgreedTerms(eq(tenantId), any(), price.capture(), any(), any(), any(), any(), any());
        return price.getValue();
    }
}
