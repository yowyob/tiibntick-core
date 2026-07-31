package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.DebitWalletCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PacketDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RespondAnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.INegotiationChatPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.RespondToAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AnnouncementUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.AnnouncementSubscriptionRepository;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * GOFP announcement orchestrator — delegates lifecycle to {@link IDeliveryAnnouncementPort}
 * (backed by {@code tnt-delivery-core}). Local {@link Announcement} persistence is kept only
 * for soft migration / legacy reads, not as a second business lifecycle.
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
    private final IWalletUseCase walletUseCase;
    private final INegotiationChatPort negotiationChatPort;
    private final IAnnouncementRepository announcementRepository;
    private final AnnouncementSubscriptionRepository subscriptionRepository;

    @Override
    @RequirePermission(resource = "announcement", action = "create")
    public Mono<AnnouncementResponseDTO> createAnnouncement(AnnouncementRequestDTO request) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> {
                    PublishAnnouncementPortCommand cmd = toPublishCommand(tenantId, request);
                    return deliveryAnnouncementPort.publish(cmd)
                            .flatMap(published -> escrowOnPublishIfFixed(published)
                                    .thenReturn(published))
                            .flatMap(published -> softMirrorLocal(published, request)
                                    .thenReturn(toDTO(published)));
                });
    }

    @Override
    public Flux<AnnouncementResponseDTO> getAllAnnouncements() {
        return tenantContextHolder.currentTenantId()
                .flatMapMany(deliveryAnnouncementPort::findOpenAnnouncements)
                .map(this::toDTO);
    }

    @Override
    public Mono<AnnouncementResponseDTO> getAnnouncement(UUID id) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, id))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Announcement not found: " + id)))
                .map(this::toDTO);
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
        return announcementRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Local announcement mirror not found: " + id
                                + " — prefer cancel + republish via delivery-core")))
                .flatMap(a -> {
                    if (request.getPaymentMethod() != null) a.setPaymentMethod(request.getPaymentMethod());
                    if (request.getTransportMethod() != null) a.setTransportMethod(request.getTransportMethod());
                    if (request.getDistance() != null) a.setDistance(request.getDistance());
                    if (request.getLogisticsPrice() != null) a.setLogisticsPrice(request.getLogisticsPrice());
                    if (request.getRequiredVehicleType() != null) a.setRequiredVehicleType(request.getRequiredVehicleType());
                    if (request.getAmount() != null) a.setAmount(request.getAmount());
                    if (request.getCurrency() != null) a.setCurrency(request.getCurrency());
                    return announcementRepository.save(a);
                })
                .map(this::localToDTO);
    }

    @Override
    public Mono<Void> deleteAnnouncement(UUID id) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, id)
                        .flatMap(a -> deliveryAnnouncementPort.cancel(tenantId, id, a.clientId())))
                .then(announcementRepository.deleteById(id).onErrorResume(e -> Mono.empty()));
    }

    @Override
    @RequirePermission(resource = "announcement", action = "create")
    public Mono<AnnouncementResponseDTO> publishAnnouncement(UUID id) {
        // createAnnouncement already publishes via delivery-core; this endpoint is idempotent read.
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

        return freelancerQuotaService.hasRemainingQuota(freelancerId)
                .flatMap(hasQuota -> {
                    if (!Boolean.TRUE.equals(hasQuota)) {
                        return Mono.error(new IllegalStateException(
                                "Freelancer has no remaining delivery quota: " + freelancerId));
                    }
                    return tenantContextHolder.currentTenantId()
                            .flatMap(tenantId -> deliveryAnnouncementPort.respond(
                                    new RespondToAnnouncementPortCommand(
                                            tenantId,
                                            announcementId,
                                            freelancerId,
                                            eta,
                                            request.getNote(),
                                            request.getProposedPrice(),
                                            request.getProposedCurrency())))
                            .flatMap(saved -> negotiationChatPort
                                    .openNegotiationThread(announcementId, saved.clientId(), freelancerId)
                                    .onErrorResume(e -> Mono.empty())
                                    .thenReturn(toCandidateDTO(saved, freelancerId)));
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
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, announcementId))
                .flatMapMany(a -> Flux.fromIterable(a.responses()))
                .map(r -> {
                    SubscriptionResponseDTO dto = new SubscriptionResponseDTO();
                    dto.setSubscriptionId(r.id());
                    dto.setFreelancerId(r.deliveryPersonId());
                    dto.setStatus(r.status());
                    dto.setCreatedAt(r.createdAt());
                    return dto;
                })
                .switchIfEmpty(subscriptionRepository.findAllByAnnouncementId(announcementId)
                        .map(s -> {
                            SubscriptionResponseDTO dto = new SubscriptionResponseDTO();
                            dto.setSubscriptionId(s.getId());
                            dto.setFreelancerId(s.getFreelancerId());
                            dto.setStatus(s.getStatus());
                            dto.setCreatedAt(s.getCreatedAt());
                            return dto;
                        }));
    }

    @Override
    @RequirePermission(resource = "announcement", action = "elect")
    public Mono<AnnouncementResponseDTO> assignResponse(
            UUID announcementId, UUID clientId, UUID responseId) {
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
                            Mono<Void> escrow = escrowOnSelectIfQuote(tenantId, before, responseId);
                            return escrow.then(deliveryAnnouncementPort.selectResponse(
                                    tenantId, announcementId, clientId, responseId));
                        }))
                .flatMap(assigned -> softMirrorAssigned(assigned).thenReturn(toDTO(assigned)));
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
        return subscriptionRepository.findAllByFreelancerId(freelancerId)
                .map(s -> s.getAnnouncementId())
                .distinct()
                .concatMap(announcementId -> getAnnouncement(announcementId)
                        .onErrorResume(e -> Mono.empty()))
                .switchIfEmpty(announcementRepository.findAllByAssignedFreelancerId(freelancerId)
                        .map(this::localToDTO));
    }

    // ── Escrow ───────────────────────────────────────────────────────────────

    private Mono<Void> escrowOnPublishIfFixed(AnnouncementSnapshot announcement) {
        if (!AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE.equals(announcement.pricingMode())) {
            return Mono.empty();
        }
        BigDecimal amount = announcement.offeredAmount();
        if (amount == null || amount.signum() <= 0) {
            return Mono.empty();
        }
        String currency = announcement.currency() != null ? announcement.currency() : "XAF";
        return walletUseCase.debitWallet(new DebitWalletCommand(
                        announcement.clientId(),
                        announcement.tenantId(),
                        Money.of(amount, currency),
                        announcement.id().toString(),
                        PaymentChannel.WALLET,
                        "Escrow FIXED_PRICE announcement " + announcement.id(),
                        "gofp-escrow-publish-" + announcement.id()))
                .doOnSuccess(tx -> log.info(
                        "[Escrow] Debited {} {} from client {} for FIXED_PRICE announcement {}",
                        amount, currency, announcement.clientId(), announcement.id()))
                .then();
    }

    /**
     * Resolves the escrow amount via {@link IDeliveryAnnouncementPort#resolveEscrowAmount} —
     * tnt-delivery-core owns the FIXED_PRICE/QUOTE_REQUEST resolution rule (see
     * {@code DeliveryAnnouncement.resolveEscrowAmount}); this method only reads the response's
     * currency, which is already available on the snapshot.
     */
    private Mono<Void> escrowOnSelectIfQuote(UUID tenantId, AnnouncementSnapshot announcement, UUID responseId) {
        if (!AnnouncementSnapshot.PRICING_MODE_QUOTE_REQUEST.equals(announcement.pricingMode())) {
            return Mono.empty();
        }
        AnnouncementResponseSnapshot selected = announcement.responses().stream()
                .filter(r -> r.id().equals(responseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Response not found: " + responseId));
        String currency = selected.proposedCurrency() != null
                ? selected.proposedCurrency()
                : (announcement.currency() != null ? announcement.currency() : "XAF");
        return deliveryAnnouncementPort.resolveEscrowAmount(tenantId, announcement.id(), responseId)
                .flatMap(amount -> walletUseCase.debitWallet(new DebitWalletCommand(
                                announcement.clientId(),
                                announcement.tenantId(),
                                Money.of(amount, currency),
                                announcement.id().toString(),
                                PaymentChannel.WALLET,
                                "Escrow QUOTE_REQUEST announcement " + announcement.id()
                                        + " response " + responseId,
                                "gofp-escrow-select-" + announcement.id() + "-" + responseId))
                        .doOnSuccess(tx -> log.info(
                                "[Escrow] Debited {} {} from client {} for QUOTE select on announcement {}",
                                amount, currency, announcement.clientId(), announcement.id())))
                .then();
    }

    // ── Soft local mirror (migration douce) ─────────────────────────────────

    private Mono<Void> softMirrorLocal(AnnouncementSnapshot published, AnnouncementRequestDTO request) {
        Announcement local = new Announcement();
        local.setId(published.id());
        local.setClientId(published.clientId());
        local.setTitle(published.title());
        local.setStatus(AnnouncementStatus.PUBLISHED);
        local.setPaymentMethod(request.getPaymentMethod());
        local.setTransportMethod(request.getTransportMethod());
        local.setDistance(request.getDistance());
        local.setLogisticsPrice(request.getLogisticsPrice());
        local.setRequiredVehicleType(request.getRequiredVehicleType());
        local.setDestinationRelayPointId(request.getDestinationRelayPointId());
        local.setShipperFirstName(request.getShipperFirstName());
        local.setShipperLastName(request.getShipperLastName());
        local.setShipperEmail(request.getShipperEmail());
        local.setShipperPhone(request.getShipperPhone());
        local.setRecipientFirstName(request.getRecipientFirstName());
        local.setRecipientLastName(request.getRecipientLastName());
        local.setRecipientEmail(request.getRecipientEmail());
        local.setRecipientPhone(request.getRecipientPhone());
        if (published.offeredAmount() != null) {
            local.setAmount(published.offeredAmount().doubleValue());
        }
        local.setCurrency(published.currency());
        return announcementRepository.save(local)
                .onErrorResume(e -> {
                    log.warn("[Mirror] Could not soft-mirror announcement {}: {}",
                            published.id(), e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> softMirrorAssigned(AnnouncementSnapshot assigned) {
        return announcementRepository.findById(assigned.id())
                .flatMap(local -> {
                    local.setStatus(AnnouncementStatus.ASSIGNED);
                    local.setAssignedFreelancerId(
                            assigned.responses().stream()
                                    .filter(r -> r.id().equals(assigned.selectedResponseId()))
                                    .map(AnnouncementResponseSnapshot::deliveryPersonId)
                                    .findFirst()
                                    .orElse(null));
                    return announcementRepository.save(local);
                })
                .onErrorResume(e -> Mono.empty())
                .then();
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

        String recipientName = joinName(request.getRecipientFirstName(), request.getRecipientLastName());
        if (recipientName.isBlank()) {
            recipientName = "Recipient";
        }
        String phone = request.getRecipientPhone() != null ? request.getRecipientPhone() : "+237000000000";

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
                phone);
    }

    private static Address toAddress(AddressDTO dto) {
        return dto != null ? dto.getAddress() : null;
    }

    private static String joinName(String first, String last) {
        String f = first != null ? first.trim() : "";
        String l = last != null ? last.trim() : "";
        return (f + " " + l).trim();
    }

    private AnnouncementResponseDTO toDTO(AnnouncementSnapshot a) {
        return toCandidateDTO(a, null);
    }

    /**
     * When {@code viewerFreelancerId} is non-null, other freelancers' proposed prices are redacted.
     */
    private AnnouncementResponseDTO toCandidateDTO(AnnouncementSnapshot a, UUID viewerFreelancerId) {
        AnnouncementResponseDTO dto = new AnnouncementResponseDTO();
        dto.setId(a.id());
        dto.setClientId(a.clientId());
        dto.setTitle(a.title());
        dto.setDescription(a.description());
        dto.setStatus(a.status());
        dto.setCreatedAt(a.createdAt());
        dto.setUpdatedAt(a.updatedAt());
        if (a.offeredAmount() != null) {
            dto.setAmount(a.offeredAmount().doubleValue());
        }
        dto.setCurrency(a.currency());
        dto.setPricingMode(a.pricingMode());
        dto.setRecipientPhone(a.recipientPhone());
        dto.setRecipientFirstName(a.recipientName());
        if (a.selectedResponseId() != null) {
            a.responses().stream()
                    .filter(r -> r.id().equals(a.selectedResponseId()))
                    .findFirst()
                    .ifPresent(r -> dto.setAssignedFreelancerId(r.deliveryPersonId()));
        }
        // Asymmetry: viewerFreelancerId is used by getAnnouncementForCandidate;
        // full client DTO keeps amounts on the announcement itself (not peer offers).
        if (viewerFreelancerId != null) {
            log.trace("[Asymmetry] Candidate view for freelancer {} on announcement {}",
                    viewerFreelancerId, a.id());
        }
        return dto;
    }

    private AnnouncementResponseDTO localToDTO(Announcement a) {
        AnnouncementResponseDTO dto = new AnnouncementResponseDTO();
        dto.setId(a.getId());
        dto.setPaymentMethod(a.getPaymentMethod());
        dto.setTransportMethod(a.getTransportMethod());
        dto.setDistance(a.getDistance());
        dto.setLogisticsPrice(a.getLogisticsPrice());
        dto.setRequiredVehicleType(a.getRequiredVehicleType());
        dto.setDestinationRelayPointId(a.getDestinationRelayPointId());
        dto.setAssignedFreelancerId(a.getAssignedFreelancerId());
        dto.setShipperFirstName(a.getShipperFirstName());
        dto.setShipperLastName(a.getShipperLastName());
        dto.setShipperEmail(a.getShipperEmail());
        dto.setShipperPhone(a.getShipperPhone());
        dto.setRecipientFirstName(a.getRecipientFirstName());
        dto.setRecipientLastName(a.getRecipientLastName());
        dto.setRecipientEmail(a.getRecipientEmail());
        dto.setRecipientPhone(a.getRecipientPhone());
        dto.setAmount(a.getAmount());
        dto.setCurrency(a.getCurrency());
        dto.setStatus(a.getStatus());
        return dto;
    }
}
