package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleCategory;
import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PacketDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RespondAnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AnnouncementUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.INegotiationChatPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementExtras;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.UpdateAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.RespondToAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpDeliveryOtpProperties;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.CodedStatusException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * GOFP announcement orchestrator — delegates the whole lifecycle to {@link IDeliveryAnnouncementPort}
 * (backed by {@code tnt-delivery-core}). No announcement is stored on the gofp side: the sender,
 * the recipient e-mail, the payment/transport/vehicle preferences, the route estimate, the
 * destination hub, the handover signature and the service fee all live on the core announcement.
 *
 * <p>This service never imports {@code tnt-delivery-core} types directly — see
 * {@code architecture/decisions.md} ADR-021. All translation to/from that module's own
 * aggregate happens in {@code DeliveryAnnouncementPortAdapter}.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementApplicationService implements AnnouncementUseCase {

    private final IDeliveryAnnouncementPort deliveryAnnouncementPort;
    private final TenantContextHolder tenantContextHolder;
    private final FreelancerQuotaService freelancerQuotaService;
    private final INegotiationChatPort negotiationChatPort;
    private final GofpFreelancerRepository gofpFreelancerRepository;
    private final GofpUserRepository gofpUserRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryOtpService deliveryOtpService;
    private final GofpDeliveryOtpProperties deliveryOtpProperties;
    /**
     * Delivery lifecycle owned by tnt-delivery-core: the agreed price and windows are recorded there at
     * selection. Mandatory: without it the delivery would have no price and would be completed unpaid.
     */
    private final com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort deliveryLifecycle;

    /** Lien annonce ↔ besoin Go (injection optionnelle, cf. DeliveryNeedApplicationService). */
    /** Lot C-36 — in-app notifications of the journey (absent in narrow tests). */
    private InAppEvents inAppEvents;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setInAppEvents(InAppEvents inAppEvents) {
        this.inAppEvents = inAppEvents;
    }

    private Mono<Void> inApp(java.util.function.Function<InAppEvents, Mono<Void>> event) {
        return inAppEvents == null ? Mono.empty() : event.apply(inAppEvents);
    }

    private NeedAnnouncementService needAnnouncements;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setNeedAnnouncements(NeedAnnouncementService needAnnouncements) {
        this.needAnnouncements = needAnnouncements;
    }

    /**
     * Lot C-54 — fiche du livreur sur chaque candidature, tri des candidatures, filtre véhicule des
     * annonces proches. Injection optionnelle (setter) : absente, fiches vides et ordre du core.
     */
    private CourierCardService courierCards;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setCourierCards(CourierCardService courierCards) {
        this.courierCards = courierCards;
    }

    @Override
    @RequirePermission(resource = "announcement", action = "create")
    public Mono<AnnouncementResponseDTO> createAnnouncement(AnnouncementRequestDTO request) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> {
                    PublishAnnouncementPortCommand cmd = toPublishCommand(tenantId, request);
                    return deliveryAnnouncementPort.publish(cmd).map(this::toDTO);
                });
    }

    @Override
    public Flux<AnnouncementResponseDTO> getAllAnnouncements() {
        return tenantContextHolder.currentTenantId()
                .flatMapMany(deliveryAnnouncementPort::findOpenAnnouncements)
                .map(this::toDTO);
    }

    @Override
    public Flux<AnnouncementResponseDTO> getNearbyAnnouncements(
            com.yowyob.tiibntick.core.gofreelancer.application.port.in.query.NearbyCriteria criteria) {
        // Mêmes champs que getAllAnnouncements : seul l'ensemble des annonces change.
        return tenantContextHolder.currentTenantId()
                .flatMapMany(tenantId -> deliveryAnnouncementPort.findOpenAnnouncementsNear(tenantId, criteria))
                .map(this::toDTO);
    }

    @Override
    public Flux<AnnouncementResponseDTO> getNearbyAnnouncementsFor(
            com.yowyob.tiibntick.core.gofreelancer.application.port.in.query.NearbyCriteria criteria,
            UUID viewerUserId) {
        Mono<VehicleCategory> viewerCategory = viewerCategory(viewerUserId).cache();
        return getNearbyAnnouncements(criteria)
                .filterWhen(dto -> {
                    VehicleCategory required = VehicleCategory.parse(dto.getRequiredVehicleType());
                    if (required == null) return Mono.just(true);
                    return viewerCategory.map(required::equals).defaultIfEmpty(false);
                });
    }

    /** Vehicle category of the courier behind this core user id; empty when unknown. */
    private Mono<VehicleCategory> viewerCategory(UUID viewerUserId) {
        if (viewerUserId == null || courierCards == null) return Mono.empty();
        return gofpFreelancerRepository.findByCoreUserId(viewerUserId)
                .flatMap(courierCards::cardOf)
                .flatMap(card -> Mono.justOrEmpty(VehicleCategory.parse(card.vehicleType())))
                .onErrorResume(e -> Mono.empty());
    }

    @Override
    public Mono<AnnouncementResponseDTO> getAnnouncement(UUID id) {
        // Lot C-56 — lue dans le tenant de l'annonce (le livreur qui la consulte porte le sien).
        return announcementTenant(id)
                .flatMap(tenant -> TenantContextHolder.underTenant(tenant.orElse(null),
                        tenantContextHolder.currentTenantId()))
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, id))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Announcement not found: " + id)))
                .map(this::toDTO)
                .flatMap(this::enrichAssignedFreelancerProfile);
    }

    @Override
    public Mono<AnnouncementResponseDTO> getAnnouncementForCandidate(UUID id, UUID viewerFreelancerId) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, id))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Announcement not found: " + id)))
                .map(a -> toCandidateDTO(a, viewerFreelancerId));
    }

    @Override
    public Flux<AnnouncementResponseDTO> getAnnouncementsByClientId(UUID clientId) {
        return tenantContextHolder.currentTenantId()
                .flatMapMany(tenantId -> deliveryAnnouncementPort.findByClient(tenantId, clientId))
                .map(this::toDTO);
    }

    @Override
    @Deprecated
    public Mono<AnnouncementResponseDTO> updateAnnouncement(UUID id, AnnouncementRequestDTO request) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, id)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Announcement not found: " + id)))
                        .flatMap(a -> {
                            // Lot C-46 : une fois un livreur retenu (livraison créée), le mode de paiement et le
                            // prix ne bougent plus — le paiement de la course les lit.
                            boolean locked = AnnouncementViews.selectedDeliveryPerson(a) != null
                                    || (a.status() != null && a.status() != AnnouncementStatus.DRAFT
                                        && a.status() != AnnouncementStatus.PUBLISHED
                                        && a.status() != AnnouncementStatus.IN_NEGOTIATION);
                            boolean amountChanged = request.getAmount() != null
                                    && (a.offeredAmount() == null
                                        || a.offeredAmount().compareTo(BigDecimal.valueOf(request.getAmount())) != 0);
                            boolean paymentChanged = request.getPaymentMethod() != null
                                    && !request.getPaymentMethod().equals(a.extras().paymentMethod());
                            if (locked && (amountChanged || paymentChanged)) {
                                return Mono.error(new org.springframework.web.server.ResponseStatusException(
                                        org.springframework.http.HttpStatus.CONFLICT,
                                        "The payment method and price of an assigned announcement can no longer change"));
                            }
                            String senderName = request.getShipperFirstName() != null
                                    || request.getShipperLastName() != null
                                    ? AnnouncementViews.joinName(
                                            request.getShipperFirstName() != null ? request.getShipperFirstName()
                                                    : AnnouncementViews.firstName(a.extras().senderName()),
                                            request.getShipperLastName() != null ? request.getShipperLastName()
                                                    : AnnouncementViews.lastName(a.extras().senderName()))
                                    : null;
                            AnnouncementExtras patch = new AnnouncementExtras(
                                    senderName, request.getShipperPhone(), request.getShipperEmail(),
                                    null, null,
                                    request.getRequiredVehicleType(), request.getPaymentMethod(),
                                    request.getTransportMethod(), toBigDecimal(request.getDistance()),
                                    request.getDuration(), request.getDestinationRelayPointId(),
                                    request.getSignatureUrl(), toBigDecimal(request.getLogisticsPrice()),
                                    null, null);
                            return deliveryAnnouncementPort.update(new UpdateAnnouncementPortCommand(
                                    tenantId, id, a.clientId(),
                                    amountChanged ? BigDecimal.valueOf(request.getAmount()) : null,
                                    amountChanged ? request.getCurrency() : null, patch));
                        }))
                .map(this::toDTO)
                .flatMap(this::enrichAssignedFreelancerProfile);
    }

    private static BigDecimal toBigDecimal(Double v) {
        return v != null ? BigDecimal.valueOf(v) : null;
    }

    @Override
    public Mono<Void> deleteAnnouncement(UUID id) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, id)
                        .flatMap(a -> deliveryAnnouncementPort.cancel(tenantId, id, a.clientId())))
                .then();
    }

    @Override
    @RequirePermission(resource = "announcement", action = "create")
    public Mono<AnnouncementResponseDTO> publishAnnouncement(UUID id) {
        // createAnnouncement already publishes via delivery-core; this endpoint is an idempotent read.
        return getAnnouncement(id);
    }

    @Override
    @RequirePermission(resource = "announcement", action = "respond")
    public Mono<AnnouncementResponseDTO> respondToAnnouncement(
            UUID announcementId, RespondAnnouncementRequestDTO request) {
        UUID freelancerId = request.getFreelancerId();
        Instant eta = request.getEstimatedArrivalTime() != null
                ? request.getEstimatedArrivalTime()
                : Instant.now().plusSeconds(1800);

        // Lot C-56 — la réponse s'écrit dans le tenant de l'annonce, pas dans celui du livreur.
        Mono<UUID> announcementTenant = deliveryAnnouncementPort.tenantOf(announcementId);
        return (announcementTenant == null ? Mono.<UUID>empty() : announcementTenant)
                .onErrorResume(e -> Mono.empty())
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(tenant -> TenantContextHolder.underTenant(tenant.orElse(null),
                        respondInCurrentTenant(announcementId, request, freelancerId, eta)));
    }

    /** Lot C-56 — tenant où l'annonce a été publiée ; vide si inconnu (le tenant de l'appelant s'applique). */
    private Mono<java.util.Optional<UUID>> announcementTenant(UUID announcementId) {
        Mono<UUID> tenant = deliveryAnnouncementPort.tenantOf(announcementId);
        return (tenant == null ? Mono.<UUID>empty() : tenant)
                .onErrorResume(e -> Mono.empty())
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty());
    }

    private Mono<AnnouncementResponseDTO> respondInCurrentTenant(UUID announcementId,
            RespondAnnouncementRequestDTO request, UUID freelancerId, Instant eta) {
        return freelancerQuotaService.hasRemainingQuota(freelancerId)
                .flatMap(hasQuota -> {
                    if (!Boolean.TRUE.equals(hasQuota)) {
                        return Mono.error(new IllegalStateException(
                                "Freelancer has no remaining delivery quota: " + freelancerId));
                    }
                    return tenantContextHolder.currentTenantId()
                            .flatMap(tenantId -> deliveryAnnouncementPort
                                    .hasResponse(announcementId, freelancerId)
                                    .flatMap(alreadyResponded -> {
                                        if (Boolean.TRUE.equals(alreadyResponded)) {
                                            log.info("Freelancer {} already responded to announcement {} — idempotent return",
                                                    freelancerId, announcementId);
                                            return deliveryAnnouncementPort
                                                    .findById(tenantId, announcementId)
                                                    .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                                            "Announcement not found: " + announcementId)));
                                        }
                                        return deliveryAnnouncementPort.respond(
                                                new RespondToAnnouncementPortCommand(
                                                        tenantId,
                                                        announcementId,
                                                        freelancerId,
                                                        eta,
                                                        request.getNote(),
                                                        request.getProposedPrice(),
                                                        request.getProposedCurrency()))
                                                .flatMap(saved -> negotiationChatPort
                                                        .openNegotiationThread(announcementId, saved.clientId(), freelancerId)
                                                        .onErrorResume(e -> Mono.empty())
                                                        .thenReturn(saved))
                                                // Lot C-36 — the sender is told a courier applied.
                                                .flatMap(saved -> inApp(n -> n.candidateApplied(announcementId,
                                                        freelancerId, priceOf(saved, freelancerId,
                                                                request.getProposedPrice()))).thenReturn(saved));
                                    }))
                            .map(saved -> toCandidateDTO(saved, freelancerId));
                });
    }

    @Override
    @Deprecated
    @RequirePermission(resource = "announcement", action = "respond")
    public Mono<Void> initiateSubscription(UUID announcementId, UUID freelancerId) {
        RespondAnnouncementRequestDTO req = new RespondAnnouncementRequestDTO();
        req.setFreelancerId(freelancerId);
        req.setEstimatedArrivalTime(Instant.now().plusSeconds(1800));
        return respondToAnnouncement(announcementId, req).then();
    }

    @Override
    public Flux<SubscriptionResponseDTO> getSubscriptionsForAnnouncement(UUID announcementId) {
        return rankSubscriptions(rawSubscriptions(announcementId));
    }

    /**
     * Lot C-54 — courier card on each application, then: available couriers first, then busy (an
     * unfinished delivery), then paused; inside each group the highest loyalty score first. The score
     * itself (and the profile creation date it comes from) never leaves the server.
     */
    Flux<SubscriptionResponseDTO> rankSubscriptions(Flux<SubscriptionResponseDTO> subscriptions) {
        if (courierCards == null) return subscriptions;
        return subscriptions
                .concatMap(this::withCard)
                .collectList()
                .flatMapMany(list -> {
                    List<RankedSubscription> sorted = new java.util.ArrayList<>(list);
                    sorted.sort(java.util.Comparator
                            .comparingInt((RankedSubscription r) -> statusRank(r.dto().getCourierStatus()))
                            .thenComparing(java.util.Comparator.comparingDouble(RankedSubscription::loyalty).reversed()));
                    return Flux.fromIterable(sorted).map(RankedSubscription::dto);
                });
    }

    private record RankedSubscription(SubscriptionResponseDTO dto, double loyalty) { }

    static int statusRank(String status) {
        if (FreelancerCandidateDTO.AVAILABLE.equals(status)) return 0;
        if (FreelancerCandidateDTO.BUSY.equals(status)) return 1;
        return 2;
    }

    private Mono<RankedSubscription> withCard(SubscriptionResponseDTO dto) {
        if (dto.getFreelancerId() == null) {
            dto.setCourierStatus(GofpFreelancer.AVAILABILITY_PAUSED);
            return Mono.just(new RankedSubscription(dto, 0));
        }
        return findFreelancer(dto.getFreelancerId())
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .onErrorReturn(java.util.Optional.empty())
                .flatMap(gofp -> {
                    Mono<CourierCardService.Card> card = gofp.isPresent()
                            ? courierCards.cardOf(gofp.get()) : courierCards.cardOfWireId(dto.getFreelancerId());
                    return Mono.zip(card.defaultIfEmpty(CourierCardService.Card.UNKNOWN),
                                    courierStatusOf(gofp.orElse(null), dto.getFreelancerId()))
                            .map(t -> {
                                CourierCardService.Card c = t.getT1();
                                dto.setVehicleType(c.vehicleType());
                                dto.setVehiclePlate(c.vehiclePlate());
                                dto.setVehiclePhotoUrls(c.vehiclePhotoUrls());
                                dto.setDeliveredCount(c.deliveredCount());
                                dto.setBadge(c.badge());
                                dto.setCourierStatus(t.getT2());
                                return new RankedSubscription(dto, c.loyaltyScore());
                            });
                })
                .onErrorResume(e -> Mono.just(new RankedSubscription(dto, 0)));
    }

    private Mono<String> courierStatusOf(GofpFreelancer gofp, UUID wireId) {
        if (gofp != null && gofp.isPaused()) return Mono.just(GofpFreelancer.AVAILABILITY_PAUSED);
        if (deliveryRepository == null) return Mono.just(FreelancerCandidateDTO.AVAILABLE);
        List<UUID> ids = new java.util.ArrayList<>();
        ids.add(wireId);
        if (gofp != null) {
            if (gofp.getId() != null && !ids.contains(gofp.getId())) ids.add(gofp.getId());
            if (gofp.getCoreFreelancerId() != null && !ids.contains(gofp.getCoreFreelancerId())) {
                ids.add(gofp.getCoreFreelancerId());
            }
        }
        return deliveryRepository.countUnfinishedByFreelancerIds(ids)
                .map(n -> n > 0 ? FreelancerCandidateDTO.BUSY : FreelancerCandidateDTO.AVAILABLE)
                .defaultIfEmpty(FreelancerCandidateDTO.AVAILABLE)
                .onErrorReturn(FreelancerCandidateDTO.AVAILABLE);
    }

    private Flux<SubscriptionResponseDTO> rawSubscriptions(UUID announcementId) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, announcementId))
                .flatMapMany(a -> Flux.fromIterable(a.responses()))
                .flatMap(this::toEnrichedSubscription);
    }

    @Override
    @RequirePermission(resource = "announcement", action = "elect")
    public Mono<AnnouncementResponseDTO> assignResponse(
            UUID announcementId, UUID clientId, UUID responseId) {
        return assignResponse(announcementId, clientId, responseId, null);
    }

    @Override
    @RequirePermission(resource = "announcement", action = "elect")
    public Mono<AnnouncementResponseDTO> assignResponse(
            UUID announcementId, UUID clientId, UUID responseId, UUID deliveryId) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, announcementId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "Announcement not found: " + announcementId)))
                        .flatMap(before -> {
                            boolean responseExists = before.responses().stream()
                                    .anyMatch(r -> r.id().equals(responseId));
                            if (!responseExists) {
                                return Mono.error(new IllegalArgumentException(
                                        "Response not found: " + responseId));
                            }
                            // Retry of a selection that the core already made (e.g. the agreed price could
                            // not be recorded the first time): do not select again, replay what follows.
                            // Every step below is idempotent (price recorded again, gofp row and codes
                            // created only when absent).
                            if (alreadySelected(before, responseId)) {
                                log.info("Announcement {} already assigned to response {}: replaying the follow-up",
                                        announcementId, responseId);
                                return Mono.just(before);
                            }
                            return deliveryId == null
                                    ? deliveryAnnouncementPort.selectResponse(
                                            tenantId, announcementId, clientId, responseId)
                                    : deliveryAnnouncementPort.selectResponse(
                                            tenantId, announcementId, clientId, responseId, deliveryId);
                        }))
                .flatMap(assigned -> softMirrorDeliveryAndInitOtp(assigned)
                        .flatMap(otpResult -> buildAssignResponse(assigned, otpResult)));
    }

    @Override
    @Deprecated
    @RequirePermission(resource = "announcement", action = "elect")
    public Mono<AnnouncementResponseDTO> assignFreelancer(UUID announcementId, UUID freelancerId) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, announcementId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Announcement not found: " + announcementId)))
                .flatMap(a -> {
                    AnnouncementResponseSnapshot match = a.responses().stream()
                            .filter(r -> freelancerId.equals(r.deliveryPersonId()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No response from freelancer " + freelancerId
                                            + " on announcement " + announcementId));
                    return assignResponse(announcementId, a.clientId(), match.id());
                });
    }

    @Override
    public Flux<AnnouncementResponseDTO> getSubscriptionsByFreelancerId(UUID freelancerId) {
        return findFreelancer(freelancerId)
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .onErrorReturn(java.util.Optional.empty())
                .flatMapMany(gofp -> {
                    java.util.Set<UUID> ids = new java.util.LinkedHashSet<>();
                    ids.add(freelancerId);
                    gofp.ifPresent(f -> {
                        if (f.getId() != null) ids.add(f.getId());
                        if (f.getCoreFreelancerId() != null) ids.add(f.getCoreFreelancerId());
                    });
                    return tenantContextHolder.currentTenantId()
                            .flatMapMany(tenantId -> deliveryAnnouncementPort.findRespondedBy(tenantId, ids));
                })
                .map(this::toDTO)
                .concatMap(this::enrichAssignedFreelancerProfile);
    }

    // ── Escrow (lot L5) ──────────────────────────────────────────────────────
    //
    // The paid selection holds the price in a Kernel escrow before electing (SelectionPaymentService:
    // challenge, then confirm with the client's code, then assignResponse with the delivery id the escrow
    // names). An election without payment (this method called directly, a Go need, an accepted invitation)
    // is paid afterwards into the same escrow (DeliveryPaymentApplicationService).

    // ── Livraison locale (OTP) ───────────────────────────────────────────────

    private static java.util.Optional<BigDecimal> selectedProposedPrice(AnnouncementSnapshot assigned) {
        return assigned.responses().stream()
                .filter(r -> r.id().equals(assigned.selectedResponseId()))
                .map(AnnouncementResponseSnapshot::proposedPrice)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    /** The core already selected this very response and created its delivery. */
    static boolean alreadySelected(AnnouncementSnapshot announcement, UUID responseId) {
        return announcement.status() == AnnouncementStatus.ASSIGNED
                && responseId.equals(announcement.selectedResponseId())
                && announcement.createdDeliveryId() != null;
    }

    /** Stable error code when the agreed terms could not be recorded on the core delivery. */
    static final String AGREED_TERMS_NOT_RECORDED = "AGREED_TERMS_NOT_RECORDED";

    /**
     * Soft-mirrors the delivery-core Delivery into the gofp {@code deliveries} table (same UUID)
     * and initialises OTP hashes when absent.
     */
    private Mono<OtpInitResult> softMirrorDeliveryAndInitOtp(AnnouncementSnapshot assigned) {
        UUID deliveryId = assigned.createdDeliveryId();
        if (deliveryId == null) {
            log.warn("[Mirror] Announcement {} has no createdDeliveryId after select", assigned.id());
            return Mono.just(new OtpInitResult(null, null, null, false));
        }
        Instant now = Instant.now();
        // The Go need a request was taken from is carried by the announcement itself (its origin extras).
        Mono<java.util.Optional<UUID>> linkedNeed = Mono.just(java.util.Optional.ofNullable(
                AnnouncementExtras.ORIGIN_DELIVERY_NEED.equals(assigned.extras().originRefType())
                        ? assigned.extras().originRefId() : null));
        // The status, the assigned delivery person, the price and the windows are the core delivery's: the
        // agreed terms are recorded there FIRST (unless already recorded), and a failure fails the selection
        // (a delivery without its price would be completed unpaid). Only then is the product extension row
        // created when absent, then the handover codes when absent. Each step is idempotent, so selecting the
        // same response again after a failure finishes the job.
        return linkedNeed.flatMap(needId -> ensureAgreedTermsInCore(assigned, deliveryId, needId.isPresent(), now)
                .then(deliveryRepository.findById(deliveryId)
                        .switchIfEmpty(Mono.defer(() -> {
                            Delivery local = new Delivery();
                            local.setId(deliveryId);
                            local.setAnnouncementId(assigned.id());
                            // Course issue d'un besoin Go : la livraison reste rattachée au besoin,
                            // donc le paiement lit le mode du besoin (CASH → règlement espèces).
                            needId.ifPresent(local::setDeliveryNeedId);
                            return deliveryRepository.save(local)
                                    .onErrorResume(e -> {
                                        log.warn("[Mirror] Could not soft-mirror delivery {}: {}",
                                                deliveryId, e.getMessage());
                                        return Mono.empty();
                                    });
                        })))
                .flatMap(deliveryOtpService::initOtpIfAbsentWithCodes))
                .defaultIfEmpty(new OtpInitResult(null, null, null, false));
    }

    /**
     * Records on the core delivery the terms agreed at selection: the price (the offered amount; for a
     * request taken from a Go need, which has no offered price, the selected response's proposed price)
     * and the default pickup / hand-over windows (collection within 4 h, hand-over between 1 h and 24 h
     * from now). A failure is propagated as a 503 {@value #AGREED_TERMS_NOT_RECORDED}: the selection fails
     * instead of leaving a delivery without its price. The core selection itself is not rolled back; selecting
     * the same response again replays this step (see {@link #alreadySelected}).
     */
    private Mono<Void> ensureAgreedTermsInCore(AnnouncementSnapshot assigned, UUID deliveryId,
                                               boolean fromNeed, Instant now) {
        // Already recorded (a replay after success, or a delivery migrated with its terms): keep them as they
        // are, so a replay never shifts the windows.
        return deliveryLifecycle.findState(assigned.tenantId(), deliveryId)
                .map(state -> state.agreedAmount() != null || state.pickupWindowStart() != null)
                .defaultIfEmpty(false)
                .flatMap(recorded -> recorded ? Mono.<Void>empty()
                        : recordAgreedTermsInCore(assigned, deliveryId, fromNeed, now))
                .onErrorMap(e -> !(e instanceof CodedStatusException), e -> {
                    log.error("[Mirror] Could not record the agreed terms of delivery {} in the core", deliveryId, e);
                    return new CodedStatusException(HttpStatus.SERVICE_UNAVAILABLE, AGREED_TERMS_NOT_RECORDED,
                            "The response is selected but its agreed price could not be recorded. "
                                    + "Select the same response again to finish (it will not be selected twice).");
                });
    }

    private Mono<Void> recordAgreedTermsInCore(AnnouncementSnapshot assigned, UUID deliveryId,
                                               boolean fromNeed, Instant now) {
        java.math.BigDecimal price = assigned.offeredAmount() != null ? assigned.offeredAmount()
                : fromNeed ? selectedProposedPrice(assigned).orElse(null) : null;
        return deliveryLifecycle.recordAgreedTerms(assigned.tenantId(), deliveryId, price, assigned.currency(),
                        now, now.plus(4, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), now.plus(24, ChronoUnit.HOURS))
                .then();
    }

    private Mono<AnnouncementResponseDTO> buildAssignResponse(
            AnnouncementSnapshot assigned, OtpInitResult otpResult) {
        AnnouncementResponseDTO dto = toDTO(assigned);
        dto.setDeliveryId(assigned.createdDeliveryId());
        dto.setTrackingCode(assigned.trackingCode());
        if (otpResult != null && otpResult.newlyInitialized()) {
            dto.setConfirmationCode(otpResult.pickupOtp());
            if (deliveryOtpProperties.isPreviewMode() && otpResult.deliveryOtp() != null) {
                dto.setDeliveryConfirmationCode(otpResult.deliveryOtp());
                dto.setDeliveryOtpDeliveryMode("PREVIEW_ONLY");
            }
        }
        return enrichAssignedFreelancerProfile(dto);
    }

    // ── Mapping ─────────────────────────────────────────────────────────────

    private PublishAnnouncementPortCommand toPublishCommand(UUID tenantId, AnnouncementRequestDTO request) {
        String pricingMode = request.getPricingMode() != null && !request.getPricingMode().isBlank()
                ? request.getPricingMode().trim().toUpperCase()
                : AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE;
        BigDecimal offered = request.getAmount() != null
                ? BigDecimal.valueOf(request.getAmount()) : null;
        String currency = request.getCurrency() != null ? request.getCurrency() : "XAF";

        PacketDTO packet = request.getPacket();
        double weight = packet != null && packet.getWeight() != null ? packet.getWeight() : 1.0;
        double width = packet != null && packet.getWidth() != null ? packet.getWidth() : 10.0;
        double height = packet != null && packet.getHeight() != null ? packet.getHeight() : 10.0;
        double length = packet != null && packet.getLength() != null ? packet.getLength() : 10.0;
        boolean fragile = packet != null && Boolean.TRUE.equals(packet.getFragile());
        boolean perishable = packet != null && Boolean.TRUE.equals(packet.getIsPerishable());
        String packageDescription = packet != null
                ? (packet.getDescription() != null ? packet.getDescription() : packet.getDesignation())
                : "Package";

        Address pickup = toAddress(request.getPickupAddress());
        Address delivery = toAddress(request.getDeliveryAddress());

        String recipientName = AnnouncementViews.joinName(request.getRecipientFirstName(), request.getRecipientLastName());
        // Lot C-56 — « — » quand un contact manque vraiment, jamais « Recipient » / +237000000000.
        if (recipientName.isBlank()) {
            recipientName = NeedAnnouncementService.ABSENT;
        }
        String phone = request.getRecipientPhone() != null && !request.getRecipientPhone().isBlank()
                ? request.getRecipientPhone() : NeedAnnouncementService.ABSENT;

        String title = request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle()
                : "Delivery announcement";

        return new PublishAnnouncementPortCommand(
                tenantId,
                request.getClientId(),
                title,
                request.getDescription(),
                offered,
                currency,
                pricingMode,
                weight, width, height, length,
                fragile, perishable, packageDescription,
                pickup,
                delivery,
                recipientName,
                phone,
                null,
                extrasOf(request));
    }

    private static AnnouncementExtras extrasOf(AnnouncementRequestDTO r) {
        String senderName = AnnouncementViews.joinName(r.getShipperFirstName(), r.getShipperLastName());
        return new AnnouncementExtras(
                senderName.isBlank() ? null : senderName, r.getShipperPhone(), r.getShipperEmail(),
                r.getRecipientEmail(), r.getUrgency(), r.getRequiredVehicleType(), r.getPaymentMethod(),
                r.getTransportMethod(), toBigDecimal(r.getDistance()), r.getDuration(),
                r.getDestinationRelayPointId(), r.getSignatureUrl(), toBigDecimal(r.getLogisticsPrice()),
                null, null);
    }

    private static Address toAddress(AddressDTO dto) {
        return dto != null ? dto.getAddress() : null;
    }

    private AnnouncementResponseDTO toDTO(AnnouncementSnapshot a) {
        return AnnouncementViews.toDto(a);
    }

    private AnnouncementResponseDTO toCandidateDTO(AnnouncementSnapshot a, UUID viewerFreelancerId) {
        if (viewerFreelancerId != null) {
            log.trace("[Asymmetry] Candidate view for freelancer {} on announcement {}",
                    viewerFreelancerId, a.id());
        }
        return AnnouncementViews.toDto(a);
    }

    private Mono<AnnouncementResponseDTO> enrichAssignedFreelancerProfile(AnnouncementResponseDTO dto) {
        UUID freelancerId = dto.getAssignedFreelancerId();
        if (freelancerId == null) {
            return Mono.just(dto);
        }
        return findFreelancer(freelancerId)
                .flatMap(gofp -> gofpUserRepository.findByCoreUserId(gofp.getCoreUserId())
                        .map(user -> {
                            dto.setAssignedFreelancerFirstName(user.getFirstName());
                            dto.setAssignedFreelancerLastName(user.getLastName());
                            dto.setAssignedFreelancerEmail(user.getEmail());
                            dto.setAssignedFreelancerPhone(user.getPhone());
                            return dto;
                        })
                        .defaultIfEmpty(dto))
                .defaultIfEmpty(dto)
                .onErrorResume(e -> Mono.just(dto));
    }

    private Mono<SubscriptionResponseDTO> toEnrichedSubscription(AnnouncementResponseSnapshot r) {
        SubscriptionResponseDTO dto = new SubscriptionResponseDTO();
        dto.setSubscriptionId(r.id());
        dto.setFreelancerId(r.deliveryPersonId());
        dto.setStatus(r.status());
        dto.setCreatedAt(r.createdAt());
        dto.setProposedPrice(r.proposedPrice());
        dto.setCurrency(r.proposedCurrency());
        return enrichSubscriptionProfile(dto);
    }

    private Mono<SubscriptionResponseDTO> enrichSubscriptionProfile(SubscriptionResponseDTO dto) {
        if (dto.getFreelancerId() == null) {
            return Mono.just(dto);
        }
        return findFreelancer(dto.getFreelancerId())
                .flatMap(gofp -> {
                    if (gofp.getRating() != null) {
                        dto.setRating(gofp.getRating());
                    }
                    return gofpUserRepository.findByCoreUserId(gofp.getCoreUserId())
                            .map(user -> applyUser(dto, user))
                            .defaultIfEmpty(dto);
                })
                .defaultIfEmpty(dto)
                .onErrorResume(e -> Mono.just(dto));
    }

    private static SubscriptionResponseDTO applyUser(SubscriptionResponseDTO dto, GofpUser user) {
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        return dto;
    }

    private Mono<GofpFreelancer> findFreelancer(UUID freelancerId) {
        return gofpFreelancerRepository.findById(freelancerId)
                .switchIfEmpty(gofpFreelancerRepository.findByCoreFreelancerId(freelancerId));
    }

    /** Lot C-36 — price of this courier's response (server price when set), else the one proposed. */
    private static java.math.BigDecimal priceOf(
            com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot saved,
            UUID freelancerId, java.math.BigDecimal proposed) {
        if (saved != null && saved.responses() != null) {
            for (var r : saved.responses()) {
                if (freelancerId.equals(r.deliveryPersonId()) && r.proposedPrice() != null) {
                    return r.proposedPrice();
                }
            }
        }
        return proposed;
    }
}
