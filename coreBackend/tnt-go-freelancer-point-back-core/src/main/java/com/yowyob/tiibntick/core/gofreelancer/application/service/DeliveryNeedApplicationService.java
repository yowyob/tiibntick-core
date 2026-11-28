package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpNotificationTemplates;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryNeedRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PacketDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.ParcelPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.ParcelDetails;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryNeedUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerEligibilityUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPriceQuoteUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ResourceNotFoundException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ValidationException;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing the DeliveryNeedUseCase.
 * Handles delivery need lifecycle including assignment and relay point notifications.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryNeedApplicationService implements DeliveryNeedUseCase {

    private final IDeliveryNeedRepository deliveryNeedRepository;
    private final AdminRelayPointUseCase adminRelayPointUseCase;
    private final PushNotificationPort pushNotificationPort;
    private final com.yowyob.tiibntick.core.gofreelancer.application.usecase.MatchingUseCase matchingUseCase;
    private final AddressUseCase addressUseCase;
    private final GofpUserRepository gofpUserRepository;
    private final GofpFreelancerRepository gofpFreelancerRepository;
    private final FreelancerPriceQuoteUseCase freelancerPriceQuoteUseCase;
    private final FreelancerEligibilityUseCase freelancerEligibilityUseCase;

    /**
     * Colis du besoin, enregistrés dans {@code tnt-delivery-core}. Injection optionnelle (setter) : absent,
     * un besoin sans colis fonctionne comme avant et un colis soumis est refusé.
     */
    private ParcelPort parcels;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setParcels(ParcelPort parcels) {
        this.parcels = parcels;
    }

    /**
     * Relay points, to check the destination the sender chose (D-140). Injection optionnelle (setter) : absente, la
     * destination n'est pas vérifiée (comme avant).
     */
    private com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository relayPoints;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRelayPoints(com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository relayPoints) {
        this.relayPoints = relayPoints;
    }

    /**
     * The core hub registry (D-144). Injection optionnelle (setter) : absente, la destination n'est pas vérifiée.
     */
    private com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort relayHubs;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRelayHubs(com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort relayHubs) {
        this.relayHubs = relayHubs;
    }

    /**
     * D-140, D-144: the relay destination chosen by the sender is checked in the core hub registry. The target is a
     * Go relay point id or a core hub id. Unknown to the core (no hub behind the relay point included) → 404. A hub
     * that is not APPROVED (pending, suspended, rejected, revoked) or that is closed (temporarily or permanently) →
     * 409; so is a Go relay point that is not APPROVED or that its owner paused (D-145) and a full hub (D-146: no
     * slot left; the deposit is checked again when the parcel arrives).
     */
    Mono<Void> requireProposableTarget(UUID target) {
        if (target == null || relayHubs == null) return Mono.empty();
        Mono<java.util.Optional<com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint>> relayPoint =
                relayPoints == null ? Mono.just(java.util.Optional.empty())
                        : relayPoints.findById(target)
                                .switchIfEmpty(Mono.defer(() -> relayPoints.findByCoreRelayPointId(target)))
                                .map(java.util.Optional::of)
                                .defaultIfEmpty(java.util.Optional.empty());
        return relayPoint.flatMap(rp -> {
            UUID hubId = rp.map(com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint::getCoreRelayPointId)
                    .orElse(target);
            if (rp.isPresent() && (rp.get().getStatus()
                    != com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus.APPROVED
                    || !Boolean.TRUE.equals(rp.get().getIsActive()))) {
                return Mono.<Void>error(refusedTarget("relay point " + rp.get().getStatus()
                        + (Boolean.TRUE.equals(rp.get().getIsActive()) ? "" : ", paused")));
            }
            if (hubId == null) {
                return Mono.<Void>error(unknownTarget(target));
            }
            return relayHubs.findHub(hubId, null)
                    .onErrorMap(com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException.class,
                            e -> unknownTarget(target))
                    .switchIfEmpty(Mono.error(() -> unknownTarget(target)))
                    .flatMap(hub -> {
                        boolean closed = hub.status() == com.yowyob.tiibntick.core.geo.domain.model.HubStatus.TEMPORARILY_CLOSED
                                || hub.status() == com.yowyob.tiibntick.core.geo.domain.model.HubStatus.PERMANENTLY_CLOSED;
                        boolean full = hub.status() == com.yowyob.tiibntick.core.geo.domain.model.HubStatus.FULL
                                || hub.availableSlots() <= 0;
                        if (!hub.acceptsDeposits() || closed || full) {
                            return Mono.<Void>error(refusedTarget("hub " + hub.approvalStatus() + "/" + hub.status()));
                        }
                        return Mono.<Void>empty();
                    });
        });
    }

    private static org.springframework.web.server.ResponseStatusException unknownTarget(UUID target) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                "Relay destination " + target + " is unknown to the relay hub registry");
    }

    private static org.springframework.web.server.ResponseStatusException refusedTarget(String why) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "This relay point does not accept new parcels (" + why + ")");
    }

    /**
     * Publication du besoin en annonce. Injection optionnelle (setter) : absente, le
     * besoin ne publie aucune annonce — c'est le cas des tests unitaires
     * historiques qui construisent ce service à la main.
     */
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
     * Élection d'une réponse sur l'annonce du besoin (lot C-46) : l'assignation directe passe par
     * le même chemin que le choix d'une candidature, qui crée la livraison payable. Injection
     * optionnelle (setter), comme {@link #setNeedAnnouncements}.
     */
    private com.yowyob.tiibntick.core.gofreelancer.application.port.in.AnnouncementUseCase announcements;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAnnouncements(com.yowyob.tiibntick.core.gofreelancer.application.port.in.AnnouncementUseCase announcements) {
        this.announcements = announcements;
    }

    /**
     * Lot C-52 — livraisons gofp, pour marquer « en course » un candidat qui a une livraison non
     * terminée. Injection optionnelle (setter) : absente, tout candidat est {@code AVAILABLE}.
     */
    private com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository deliveries;

    /**
     * Lot C-53 — codes de remise créés à l'élection (et non plus au premier appui du livreur).
     * Injection optionnelle (setter) : absente, {@code updateStatus} les crée toujours en filet.
     */
    private DeliveryOtpService deliveryOtp;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setDeliveryOtp(DeliveryOtpService deliveryOtp) {
        this.deliveryOtp = deliveryOtp;
    }

    /**
     * Lot C-54 — fiche du livreur (véhicule réel, immatriculation, photos, livraisons, badge) et type
     * de véhicule pour la grille plateforme. Injection optionnelle (setter) : absente, fiche inconnue
     * et grille moto.
     */
    private CourierCardService courierCards;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setCourierCards(CourierCardService courierCards) {
        this.courierCards = courierCards;
    }

    /** Lot C-54 — miroir local des annonces (lien annonce → besoin) pour le prix indicatif. Optionnel. */
    private com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository localAnnouncements;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setLocalAnnouncements(
            com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository localAnnouncements) {
        this.localAnnouncements = localAnnouncements;
    }

    /**
     * Lot C-54 — prix de la grille plateforme du véhicule de l'appelant pour le colis de l'annonce :
     * distance et colis du besoin lié (annonce publiée par un besoin Go), sinon la distance de
     * l'annonce sans colis. Jamais la politique personnelle du livreur : c'est une indication.
     */
    @Override
    public Mono<PlatformPrice> platformPriceForCourier(UUID announcementId, UUID callerUserId) {
        if (callerUserId == null) {
            return Mono.error(new org.springframework.security.access.AccessDeniedException(
                    "An authenticated courier is required"));
        }
        if (localAnnouncements == null) {
            return Mono.error(new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Announcement not found"));
        }
        Mono<GofpFreelancer> me = gofpFreelancerRepository.findByCoreUserId(callerUserId)
                .switchIfEmpty(Mono.error(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Courier profile not found for the caller")));
        return localAnnouncements.findById(announcementId)
                .switchIfEmpty(Mono.error(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Announcement not found")))
                .flatMap(a -> me.flatMap(f -> cardOf(f, f.getId())).flatMap(card -> {
                    Mono<FreelancerPriceQuoteUseCase.QuoteInput> input = a.getDeliveryNeedId() == null
                            ? Mono.just(FreelancerPriceQuoteUseCase.QuoteInput.of(
                                    a.getDistance() != null ? a.getDistance() : 0.0, 0, 0, false, false))
                            : deliveryNeedRepository.findById(a.getDeliveryNeedId())
                                    .flatMap(need -> loadPacketMetrics(need.getPacketId()).map(p ->
                                            new FreelancerPriceQuoteUseCase.QuoteInput(
                                                    need.getDistance() != null ? need.getDistance() : 0.0,
                                                    p.weightKg(), p.volumeM3(), p.fragile(), p.perishable(),
                                                    null, null, need.getPickupDeadline(), need.getPaymentMethod(),
                                                    null)))
                                    .defaultIfEmpty(FreelancerPriceQuoteUseCase.QuoteInput.of(
                                            a.getDistance() != null ? a.getDistance() : 0.0, 0, 0, false, false));
                    return input.flatMap(in -> freelancerPriceQuoteUseCase.platformQuote(card.vehicleType(), in))
                            .map(q -> {
                                var category = com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle
                                        .VehicleCategory.parse(card.vehicleType());
                                return new PlatformPrice(announcementId,
                                        category == null ? "MOTORBIKE" : category.canonical().name(),
                                        category == null ? "moto" : category.appWord(),
                                        q.totalPrice(), q.currency(), q.breakdown());
                            });
                }));
    }

    private Mono<CourierCardService.Card> cardOf(GofpFreelancer freelancer, UUID wireId) {
        if (courierCards == null) return Mono.just(CourierCardService.Card.UNKNOWN);
        return (freelancer != null ? courierCards.cardOf(freelancer) : courierCards.cardOfWireId(wireId))
                .defaultIfEmpty(CourierCardService.Card.UNKNOWN)
                .onErrorReturn(CourierCardService.Card.UNKNOWN);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setDeliveries(com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    /**
     * Active la garde d'ownership. Défaut {@code true} : un déploiement qui oublie la variable
     * doit être protégé, pas ouvert — la configuration dangereuse ne doit jamais être celle
     * obtenue par omission. Le profil {@code test} la met à {@code false}.
     *
     * <p>Attention, ce n'est pas un interrupteur suffisant : la garde ne mord que sur les
     * requêtes authentifiées (cf. {@link #resolveOwner}).
     */
    @Value("${tnt.gofp.ownership-guard.enabled:true}")
    private boolean ownershipGuardEnabled = true;

    /**
     * Identité effective du propriétaire d'un besoin, ou {@code null} si la garde est désactivée.
     *
     * <p>Une seule condition neutralise la garde : le flag {@code tnt.gofp.ownership-guard.enabled}
     * à {@code false} — désactivation explicite, assumée par la configuration. En production ce flag
     * est interdit (cf. {@code GoFreelancerPointCoreConfig#validateOwnershipGuardNotDisabledInProd}).
     *
     * <p>{@code callerId == null} n'est plus un cas de neutralisation. Un appelant authentifié
     * dont le JWT {@code sub} n'est pas un UUID valide (jeton client plate-forme, clé d'API
     * passerelle) obtient {@code userId = null} dans {@code TntSecurityContextService#parseUuidOrNull}
     * — le service lui interdit l'accès plutôt que de laisser passer silencieusement.
     *
     * @throws AccessDeniedException si la garde est active et {@code callerId} est {@code null}
     */
    private UUID resolveOwner(UUID callerId, String operation) {
        if (!ownershipGuardEnabled) {
            return null;
        }
        if (callerId == null) {
            throw new AccessDeniedException(
                    "[ownership] " + operation + " requires an authenticated identity "
                    + "(callerId is null — likely a platform-client token with a non-UUID sub)");
        }
        return callerId;
    }

    /**
     * Refuse l'accès quand le besoin existe mais n'appartient pas à l'appelant.
     *
     * <p>L'ordre compte : l'appelant a déjà été résolu <em>après</em> le chargement du besoin,
     * de sorte qu'un id inexistant sorte en 404 et jamais en 403 — un 403 sur un besoin absent
     * confirmerait son inexistence par différence, et un 404 sur un besoin existant mais
     * étranger empêcherait de distinguer un bug d'un refus légitime côté BFF.
     */
    private Mono<DeliveryNeed> requireOwnership(DeliveryNeed need, UUID callerId, String operation) {
        UUID owner = resolveOwner(callerId, operation);
        if (owner == null || owner.equals(need.getUserId())) {
            return Mono.just(need);
        }
        log.warn("[ownership] {} refusé : caller {} != owner {} (deliveryNeed {})",
                operation, owner, need.getUserId(), need.getId());
        return Mono.error(new AccessDeniedException(
                "This delivery need belongs to another user"));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Mono<DeliveryNeedResponseDTO> createDeliveryNeed(DeliveryNeedRequestDTO request, UUID callerId) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("Delivery need request is required"));
        }
        // Volet C — l'identité du propriétaire est dérivée du jeton, jamais du corps de requête :
        // sinon n'importe quel appelant crée un besoin au nom d'autrui. Le userId du body reste
        // toléré tant que la garde est neutralisée (run local sans JWT), et il est ignoré sinon.
        UUID owner = resolveOwner(callerId, "createDeliveryNeed");
        if (owner != null) {
            if (request.getUserId() != null && !owner.equals(request.getUserId())) {
                log.warn("[ownership] createDeliveryNeed : userId du body ({}) ignoré au profit "
                        + "de l'identité du jeton ({})", request.getUserId(), owner);
            }
            request.setUserId(owner);
        }
        if (request.getUserId() == null) {
            return Mono.error(new IllegalArgumentException("userId is required"));
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            return Mono.error(new IllegalArgumentException("title is required"));
        }
        if (request.getPickupAddress() == null || request.getPickupAddress().getAddress() == null) {
            return Mono.error(new IllegalArgumentException("pickupAddress is required"));
        }
        if (request.getDeliveryAddress() == null || request.getDeliveryAddress().getAddress() == null) {
            return Mono.error(new IllegalArgumentException("deliveryAddress is required"));
        }
        try {
            requiredVehicleOf(request.getRequiredVehicleType());
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return Mono.error(e);
        }

        Mono<AddressDTO> pickupMono = addressUseCase.createAddress(request.getPickupAddress());
        Mono<AddressDTO> deliveryMono = addressUseCase.createAddress(request.getDeliveryAddress());

        return requireProposableTarget(request.getTargetRelayPointId())
                .then(Mono.zip(pickupMono, deliveryMono))
                .flatMap(addresses -> persistPacketIfPresent(request.getPacket())
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .flatMap(packetId -> {
                            Instant now = Instant.now();
                            DeliveryNeed need = DeliveryNeed.builder()
                                    .id(UUID.randomUUID())
                                    .userId(request.getUserId())
                                    .packetId(packetId.orElse(null))
                                    .title(request.getTitle().trim())
                                    .description(request.getDescription())
                                    .pickupAddressId(addresses.getT1().getId())
                                    .deliveryAddressId(addresses.getT2().getId())
                                    .signatureUrl(request.getSignatureUrl())
                                    .paymentMethod(request.getPaymentMethod())
                                    .transportMethod(request.getTransportMethod())
                                    .requiredVehicleType(requiredVehicleOf(request.getRequiredVehicleType()))
                                    .distance(request.getDistance())
                                    .duration(request.getDuration())
                                    .pickupDeadline(request.getPickupDeadline())
                                    .targetRelayPointId(request.getTargetRelayPointId())
                                    .requestedStorageDays(request.getRequestedStorageDays())
                                    // Lot C-56 — contacts enregistrés, relus par l'expéditeur et le livreur.
                                    .senderFirstName(clean(request.getSenderFirstName()))
                                    .senderLastName(clean(request.getSenderLastName()))
                                    .senderPhone(clean(request.getSenderPhone()))
                                    .recipientFirstName(clean(request.getRecipientFirstName()))
                                    .recipientLastName(clean(request.getRecipientLastName()))
                                    .recipientPhone(clean(request.getRecipientPhone()))
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .build();
                            log.info("Creating DeliveryNeed {} for user {} (packetId={})",
                                    need.getId(), need.getUserId(), need.getPacketId());
                            return deliveryNeedRepository.save(need)
                                    .flatMap(saved -> publishAnnouncement(
                                            saved, addresses.getT1(), addresses.getT2(), request.getPacket())
                                            .map(Optional::of)
                                            .defaultIfEmpty(Optional.empty())
                                            .flatMap(announcementId -> {
                                                if (announcementId.isEmpty()) {
                                                    return Mono.just(saved);
                                                }
                                                saved.markNotNew();
                                                saved.setAnnouncementId(announcementId.get());
                                                return deliveryNeedRepository.save(saved);
                                            }))
                                    .flatMap(saved -> inApp(n -> n.relayChosen(saved)).thenReturn(saved));
                        }))
                .flatMap(this::enrich);
    }

    /** Publie le besoin en annonce QUOTE_REQUEST (les erreurs remontent : un seul état). */
    private Mono<UUID> publishAnnouncement(DeliveryNeed need, AddressDTO pickup, AddressDTO delivery, PacketDTO packet) {
        if (needAnnouncements == null) return Mono.empty();
        NeedAnnouncementService.ParcelSpec parcel = packet == null ? null : new NeedAnnouncementService.ParcelSpec(
                packet.getWeight(), packet.getWidth(), packet.getHeight(), packet.getLength(),
                packet.getFragile(), packet.getIsPerishable(),
                packet.getDescription() != null ? packet.getDescription() : packet.getDesignation());
        return needAnnouncements.publishFor(need, pickup.getAddress(), delivery.getAddress(), parcel);
    }

    /** L'annonce du besoin n'est plus ouverte (besoin assigné directement, ou supprimé). */
    private Mono<Void> withdrawAnnouncement(UUID deliveryNeedId) {
        return needAnnouncements == null ? Mono.empty() : needAnnouncements.withdrawFor(deliveryNeedId);
    }

    /**
     * Registers the optional packet carried by the request as a parcel in {@code tnt-delivery-core}
     * (through {@link ParcelPort}) and returns the parcel id, so the caller can wire it onto
     * {@code delivery_needs.parcel_id}: the parcel id is the id that follows the goods from the need to
     * the announcement, the delivery and the relay deposit. There is no longer a {@code packets} table.
     *
     * @return the parcel id, or an empty {@link Mono} when no packet was submitted.
     */
    private Mono<UUID> persistPacketIfPresent(PacketDTO packet) {
        if (packet == null) {
            return Mono.empty();
        }
        if (parcels == null) {
            return Mono.error(new IllegalStateException("No parcel port: cannot register the packet"));
        }
        log.info("Registering the packet as a core parcel (designation={})", packet.getDesignation());
        return parcels.register(toParcelDetails(packet));
    }

    /** Packet as the customer described it: weight in kg, dimensions in cm. */
    static ParcelDetails toParcelDetails(PacketDTO packet) {
        return new ParcelDetails(null, packet.getDesignation(), packet.getDescription(), packet.getWeight(),
                packet.getWidth(), packet.getHeight(), packet.getLength(), packet.getThickness(),
                packet.getFragile(), packet.getIsPerishable(), packet.getPhotoPacket());
    }

    /** The parcel as the API shows a packet (same fields as the DTO the customer sent). */
    static PacketDTO toPacketDTO(ParcelDetails parcel) {
        PacketDTO dto = new PacketDTO();
        dto.setDesignation(parcel.designation());
        dto.setDescription(parcel.description());
        dto.setWeight(parcel.weightKg());
        dto.setHeight(parcel.heightCm());
        dto.setWidth(parcel.widthCm());
        dto.setLength(parcel.lengthCm());
        dto.setThickness(parcel.thicknessCm());
        dto.setFragile(parcel.fragile());
        dto.setIsPerishable(parcel.perishable());
        dto.setPhotoPacket(parcel.photoUrl());
        return dto;
    }

    @Override
    public Mono<DeliveryNeedResponseDTO> getDeliveryNeed(UUID id, UUID callerId) {
        return deliveryNeedRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + id)))
                .flatMap(need -> requireOwnership(need, callerId, "getDeliveryNeed"))
                .flatMap(this::enrich);
    }

    @Override
    public Flux<DeliveryNeedResponseDTO> getDeliveryNeedsByUserId(UUID userId, UUID callerId) {
        // Pas de besoin à charger ici : la comparaison porte directement sur le path variable.
        UUID owner = resolveOwner(callerId, "getDeliveryNeedsByUserId");
        if (owner != null && !owner.equals(userId)) {
            log.warn("[ownership] getDeliveryNeedsByUserId refusé : caller {} != {}", owner, userId);
            return Flux.error(new AccessDeniedException("Cannot list another user's delivery needs"));
        }
        return listDeliveryNeedsByUserId(userId);
    }

    private Flux<DeliveryNeedResponseDTO> listDeliveryNeedsByUserId(UUID userId) {
        return enrichAll(deliveryNeedRepository.findAllByUserId(userId));
    }

    @Override
    public Mono<Void> deleteDeliveryNeed(UUID id, UUID callerId) {
        return deliveryNeedRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + id)))
                .flatMap(need -> requireOwnership(need, callerId, "deleteDeliveryNeed"))
                .flatMap(need -> withdrawAnnouncement(id).then(deliveryNeedRepository.deleteById(id)));
    }

    /**
     * Valide que {@code freelancerId} désigne un freelancer réel et assignable.
     *
     * <p>L'id attendu est l'id « fil » {@code gofp_freelancers.id} — le même que celui
     * produit par le matching et renvoyé par {@code GET /{id}/candidates}. Sans ce contrôle,
     * un id inexistant remonte en {@code 500} brut depuis la FK
     * {@code fk_delivery_needs_assigned_freelancer} (changeset 070), illisible pour l'appelant.
     *
     * <p>On ne vérifie <em>pas</em> que le freelancer figure parmi les candidats éligibles :
     * cela imposerait de rejouer le matching à chaque assignation, dont le résultat n'est pas
     * déterministe (les positions GPS bougent entre {@code /candidates} et {@code /assign}) —
     * un choix légitime du client se verrait alors refusé.
     */
    private Mono<GofpFreelancer> requireAssignableFreelancer(UUID freelancerId) {
        if (freelancerId == null) {
            return Mono.error(new ValidationException("freelancerId is required"));
        }
        return gofpFreelancerRepository.findById(freelancerId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Freelancer", "id", freelancerId)))
                .flatMap(freelancer -> {
                    if (freelancer.getStatus() != FreelancerStatus.APPROVED) {
                        return Mono.error(new IllegalStateException(
                                "Freelancer " + freelancerId + " is not assignable: status="
                                        + freelancer.getStatus() + " (expected APPROVED)"));
                    }
                    if (!Boolean.TRUE.equals(freelancer.getIsActive())) {
                        return Mono.error(new IllegalStateException(
                                "Freelancer " + freelancerId + " is not assignable: inactive"));
                    }
                    return Mono.just(freelancer);
                });
    }

    /**
     * <p><strong>Ordre des contrôles.</strong> Le besoin est résolu et sa propriété vérifiée
     * <em>avant</em> {@link #requireAssignableFreelancer} : un appelant étranger ne doit rien
     * apprendre du parc de freelancers (existence, statut) en sondant un besoin qui ne lui
     * appartient pas. La séquence est donc 404 besoin → 403 besoin → 400/404/409 freelancer.
     * L'inverse — l'ordre d'origine — reste fonctionnellement correct mais fuit de l'information
     * au premier appelant venu.
     */
    @Override
    public Mono<DeliveryNeedResponseDTO> assignFreelancer(UUID deliveryNeedId, UUID freelancerId, UUID callerId) {
        return assignFreelancer(deliveryNeedId, freelancerId, callerId, false);
    }

    /**
     * Lot C-52 — l'assignation directe n'est plus un moyen d'imposer une course : hors personnel,
     * elle n'élit qu'un livreur qui a <em>déjà postulé</em> sur l'annonce du besoin (il a dit oui).
     * Pour un livreur qui n'a pas postulé, le client l'invite ({@code /invite}) : 409 ici.
     */
    @Override
    public Mono<DeliveryNeedResponseDTO> assignFreelancer(UUID deliveryNeedId, UUID freelancerId, UUID callerId,
                                                          boolean callerIsStaff) {
        return deliveryNeedRepository.findById(deliveryNeedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + deliveryNeedId)))
                .flatMap(need -> callerIsStaff ? Mono.just(need) : requireOwnership(need, callerId, "assignFreelancer"))
                .flatMap(need -> requireAssignableFreelancer(freelancerId)
                        .flatMap(freelancer -> (callerIsStaff ? Mono.<Void>empty() : requireApplied(need, freelancer))
                                .then(Mono.defer(() -> completeAssignment(need, freelancer)))));
    }

    /**
     * Élit ce livreur sur ce besoin par le chemin unique (C-46) : annonce ouverte → réponse au prix
     * serveur puis {@code assignResponse} (livraison payable, mode de paiement du besoin) ; sans
     * annonce, ancien chemin. Partagé par l'assignation et l'acceptation d'une invitation (C-52).
     */
    public Mono<DeliveryNeedResponseDTO> completeAssignment(DeliveryNeed need, GofpFreelancer freelancer) {
        return electThroughAnnouncement(need, freelancer)
                .switchIfEmpty(Mono.defer(() -> assignWithoutAnnouncement(need, freelancer.getId())))
                .flatMap(saved -> ensureHandoverCodes(saved).thenReturn(saved))
                .flatMap(saved -> notifyRelayPoint(saved).thenReturn(saved))
                .flatMap(saved -> inApp(n -> n.courierAssigned(saved, freelancer)).thenReturn(saved))
                .flatMap(this::enrich);
    }

    /**
     * Lot C-53 — la livraison créée par l'élection a ses deux codes de remise (créés s'ils manquent,
     * jamais régénérés). Sans livraison (ancien chemin sans annonce), rien à faire. Un échec ne défait
     * pas l'élection : {@code updateStatus} reste le filet.
     */
    private Mono<Void> ensureHandoverCodes(DeliveryNeed need) {
        if (deliveries == null || deliveryOtp == null) {
            return Mono.empty();
        }
        return deliveries.findByDeliveryNeedId(need.getId())
                .flatMap(deliveryOtp::initOtpIfAbsent)
                .onErrorResume(e -> {
                    log.warn("Handover codes not created at election for need {}: {}", need.getId(), e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /** Vue d'un besoin pour un tiers déjà autorisé (le livreur invité) : sans contrôle de propriété. */
    public Mono<DeliveryNeedResponseDTO> view(DeliveryNeed need) {
        return enrich(need);
    }

    /** Le livreur a une réponse (candidature) sur l'annonce ouverte du besoin, sinon 409 « invitez-le ». */
    private Mono<Void> requireApplied(DeliveryNeed need, GofpFreelancer freelancer) {
        Mono<Boolean> applied = needAnnouncements == null
                ? Mono.just(false)
                : needAnnouncements.openAnnouncementOf(need.getId())
                        .flatMap(a -> needAnnouncements.hasResponseFrom(a.id(), responderIds(freelancer)))
                        .defaultIfEmpty(false);
        return applied.flatMap(ok -> ok ? Mono.<Void>empty()
                : Mono.error(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "This courier has not applied: invite them instead")));
    }

    private static List<UUID> responderIds(GofpFreelancer f) {
        List<UUID> ids = new java.util.ArrayList<>();
        if (f.getCoreFreelancerId() != null) ids.add(f.getCoreFreelancerId());
        ids.add(f.getId());
        return ids;
    }

    /** Statut d'un candidat : {@code BUSY} s'il a une livraison ni livrée, ni annulée, ni échouée. */
    private Mono<String> courierStatus(GofpFreelancer freelancer, UUID wireId) {
        if (deliveries == null) {
            return Mono.just(FreelancerCandidateDTO.AVAILABLE);
        }
        List<UUID> ids = freelancer != null ? responderIds(freelancer) : List.of(wireId);
        return deliveries.countUnfinishedByFreelancerIds(ids)
                .map(n -> n > 0 ? FreelancerCandidateDTO.BUSY : FreelancerCandidateDTO.AVAILABLE)
                .defaultIfEmpty(FreelancerCandidateDTO.AVAILABLE)
                .onErrorReturn(FreelancerCandidateDTO.AVAILABLE);
    }

    /**
     * Lot C-46 — le besoin a encore son annonce ouverte (C-43) : l'assignation directe y inscrit le
     * livreur au prix calculé par le serveur, puis l'élit par le même chemin que le choix d'une
     * candidature ({@link AnnouncementUseCase#assignResponse}) — celui qui crée la livraison
     * delivery-core, sa copie gofp payable (tarif = ce prix, lien vers le besoin), les codes de
     * remise, et passe le besoin ASSIGNED. Vide si le besoin n'a pas d'annonce ouverte (besoin
     * antérieur à C-43, ou déjà assigné : rejouer l'assignation ne crée pas de seconde livraison).
     */
    private Mono<DeliveryNeed> electThroughAnnouncement(DeliveryNeed need, GofpFreelancer freelancer) {
        if (needAnnouncements == null || announcements == null) {
            return Mono.empty();
        }
        UUID responderId = freelancer.getCoreFreelancerId() != null
                ? freelancer.getCoreFreelancerId() : freelancer.getId();
        // Lot C-56 — l'élection lit l'annonce dans SON tenant : le livreur qui accepte une invitation
        // porte son propre `tid`, qui peut différer de celui de l'expéditeur (sinon 404).
        return needAnnouncements.openAnnouncementOf(need.getId())
                .flatMap(announcement -> needAnnouncements.tenantOf(announcement.id())
                        .map(java.util.Optional::of)
                        .defaultIfEmpty(java.util.Optional.empty())
                        .flatMap(tenant -> TenantContextHolder.underTenant(tenant.orElse(null),
                                serverPrice(need, freelancer)
                                        .flatMap(quote -> needAnnouncements.responseAt(announcement.id(),
                                                responderId, java.math.BigDecimal.valueOf(quote.totalPrice()),
                                                quote.currency()))
                                        .flatMap(responseId -> announcements.assignResponse(
                                                announcement.id(), need.getUserId(), responseId))))
                        .then(deliveryNeedRepository.findById(need.getId())));
    }

    /**
     * Prix de ce livreur pour ce besoin, par le même moteur que la liste des candidats
     * ({@link FreelancerPriceQuoteUseCase}). Devis indisponible ou nul → 409 : jamais un tarif à 0.
     */
    private Mono<FreelancerPriceQuoteUseCase.Quote> serverPrice(DeliveryNeed need, GofpFreelancer freelancer) {
        List<UUID> ownerIds = new java.util.ArrayList<>();
        if (freelancer.getCoreFreelancerId() != null) ownerIds.add(freelancer.getCoreFreelancerId());
        if (freelancer.getCoreUserId() != null) ownerIds.add(freelancer.getCoreUserId());
        ownerIds.add(freelancer.getId());
        double distanceKm = need.getDistance() != null ? need.getDistance() : 0.0;
        return Mono.zip(loadPacketMetrics(need.getPacketId()), cardOf(freelancer, freelancer.getId()))
                .flatMap(t -> freelancerPriceQuoteUseCase.quote(ownerIds,
                        new FreelancerPriceQuoteUseCase.QuoteInput(distanceKm, t.getT1().weightKg(),
                                t.getT1().volumeM3(), t.getT1().fragile(), t.getT1().perishable(), null, null,
                                need.getPickupDeadline(), need.getPaymentMethod(), null),
                        t.getT2().vehicleType()))
                .filter(q -> q.totalPrice() > 0)
                .onErrorMap(e -> !(e instanceof org.springframework.web.server.ResponseStatusException),
                        e -> new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.CONFLICT,
                                "The price of this courier cannot be computed now", e))
                .switchIfEmpty(Mono.error(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "The price of this courier cannot be computed now")));
    }

    /**
     * Le besoin n'a pas d'annonce ouverte. Son statut étant dérivé de l'annonce et de la livraison du
     * core, il n'y a plus d'état « assigné » à écrire sur le besoin : si une annonce existe déjà (besoin
     * déjà assigné, rejeu de l'assignation) le besoin est rendu tel quel ; sinon (besoin jamais
     * publié) l'assignation est refusée (409).
     */
    private Mono<DeliveryNeed> assignWithoutAnnouncement(DeliveryNeed need, UUID freelancerId) {
        Mono<UUID> existing = needAnnouncements == null ? Mono.empty() : needAnnouncements.announcementIdOf(need.getId());
        return existing
                .flatMap(x -> deliveryNeedRepository.findById(need.getId()))
                .switchIfEmpty(Mono.error(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "This delivery need has no announcement: it cannot be assigned")));
    }

    private Mono<Void> notifyRelayPoint(DeliveryNeed need) {
        if (need.getTargetRelayPointId() == null) {
            return Mono.empty();
        }
        return adminRelayPointUseCase.getRelayPointDetails(need.getTargetRelayPointId())
                .flatMap(relayPoint -> pushNotificationPort.sendLocalized(
                        relayPoint.getFreelancerId(),
                        GofpNotificationTemplates.RELAY_DELIVERY_EXPECTED,
                        java.util.Map.of()))
                .onErrorResume(e -> {
                    log.error("Failed to notify relay point owner upon assignment", e);
                    return Mono.empty();
                })
                .then();
    }

    @Override
    public Flux<FreelancerCandidateDTO> getCandidatesWithPricing(UUID deliveryNeedId, UUID callerId) {
        return deliveryNeedRepository.findById(deliveryNeedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery need not found: " + deliveryNeedId)))
                .flatMap(need -> requireOwnership(need, callerId, "getCandidatesWithPricing"))
                .flatMapMany(need -> Mono.zip(
                                loadCoordinates(need.getPickupAddressId()),
                                loadCoordinates(need.getDeliveryAddressId()),
                                loadPacketMetrics(need.getPacketId()))
                        .flatMapMany(ctx -> {
                            GeoPoint pickup = ctx.getT1();
                            GeoPoint delivery = ctx.getT2();
                            PacketMetrics packet = ctx.getT3();
                            double distanceKm = need.getDistance() != null ? need.getDistance() : 0.0;

                            log.info("Matching DeliveryNeed {}: pickup=({},{}) delivery=({},{}) "
                                            + "distance={}km weight={}kg volume={}m3 fragile={} perishable={}",
                                    need.getId(), pickup.lat(), pickup.lon(), delivery.lat(), delivery.lon(),
                                    distanceKm, packet.weightKg(), packet.volumeM3(),
                                    packet.fragile(), packet.perishable());

                            return matchingUseCase.processMatchingForDeliveryNeed(
                                            need.getId(), pickup.lat(), pickup.lon(),
                                            delivery.lat(), delivery.lon(),
                                            packet.volumeM3(), need.getPickupDeadline(),
                                            need.getRequiredVehicleType())
                                    .flatMapMany(Flux::fromIterable)
                                    .flatMap(candidate -> toPricedCandidate(candidate, distanceKm, packet, need));
                        }));
    }

    /**
     * Assemble un candidat : politique de prix du freelancer (ou {@link #DEFAULT_POLICY}),
     * identité réelle (ou repli {@code "Freelancer"}), prix estimé et détail lisible.
     *
     * <p><strong>Trois espaces d'identité cohabitent</strong> et ne doivent pas être confondus :
     * <ul>
     *   <li>{@code candidate.freelancerId} = {@code gofp_freelancers.id} — la clé « fil »
     *       produite par le matching ({@code FreelancerProviderAdapter#fromGofp});</li>
     *   <li>le nom se lit via {@code gofp_users.core_user_id} =
     *       {@code GofpFreelancer#getCoreUserId()};</li>
     *   <li>le tarif se lit via la politique billing dont le propriétaire est
     *       {@code GofpFreelancer#getCoreFreelancerId()} (lot L4 : la table héritée
     *       {@code delivery_person_pricing} n'est plus lue).</li>
     * </ul>
     * On charge donc la ligne {@code gofp_freelancers} une seule fois pour traduire l'id fil
     * dans les deux autres espaces. Sans cette traduction, le nom retombe systématiquement sur
     * {@code "Freelancer"} et le tarif sur {@link #DEFAULT_POLICY}, silencieusement.
     */
    private Mono<FreelancerCandidateDTO> toPricedCandidate(
            TopsisRankingUseCase.FreelancerCandidate candidate, double distanceKm, PacketMetrics packet,
            DeliveryNeed need) {

        UUID freelancerId = candidate.getFreelancerId();

        return gofpFreelancerRepository.findById(freelancerId)
                .switchIfEmpty(Mono.defer(() -> gofpFreelancerRepository.findByCoreFreelancerId(freelancerId)))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(e -> {
                    log.warn("GofpFreelancer {} lookup failed — pricing/name fall back to defaults",
                            freelancerId, e);
                    return Mono.just(Optional.empty());
                })
                .flatMap(freelancer -> isEligible(candidate, freelancer.orElse(null), packet, need)
                        .filter(Boolean::booleanValue)
                        .flatMap(ok -> priceCandidate(candidate, freelancer.orElse(null), distanceKm, packet, need)));
    }

    /**
     * Les contraintes dures publiées par le livreur (colis acceptés, plage de poids, jours de travail)
     * — voir {@link FreelancerEligibilityRules}. Sans préférence enregistrée, ou si la lecture échoue,
     * le livreur reste éligible.
     */
    private Mono<Boolean> isEligible(TopsisRankingUseCase.FreelancerCandidate candidate, GofpFreelancer freelancer,
                                     PacketMetrics packet, DeliveryNeed need) {
        UUID wireId = candidate.getFreelancerId();
        List<UUID> ids = new java.util.ArrayList<>();
        if (freelancer != null && freelancer.getCoreFreelancerId() != null) ids.add(freelancer.getCoreFreelancerId());
        ids.add(wireId);
        java.time.DayOfWeek day = need != null && need.getPickupDeadline() != null
                ? need.getPickupDeadline().getDayOfWeek() : null;
        return freelancerEligibilityUseCase.isEligible(ids,
                new FreelancerEligibilityUseCase.Parcel(packet.weightKg(), packet.fragile(), packet.perishable(), day))
                .doOnNext(ok -> {
                    if (!ok) log.info("Candidate {} filtered out by its eligibility preferences", wireId);
                });
    }

    private Mono<FreelancerCandidateDTO> priceCandidate(
            TopsisRankingUseCase.FreelancerCandidate candidate,
            GofpFreelancer freelancer,
            double distanceKm, PacketMetrics packet, DeliveryNeed need) {

        UUID freelancerId = candidate.getFreelancerId();
        // Repli sur l'id fil quand la ligne gofp_freelancers est introuvable : au pire on
        // retombe sur le barème plateforme, jamais sur une exception.
        UUID pricingId = (freelancer != null && freelancer.getCoreFreelancerId() != null)
                ? freelancer.getCoreFreelancerId() : freelancerId;
        UUID identityId = (freelancer != null && freelancer.getCoreUserId() != null)
                ? freelancer.getCoreUserId() : freelancerId;

        // Le prix vient du moteur billing : politique active du livreur (grille ou template),
        // sinon grille d'avant migration, sinon barème plateforme — voir FreelancerPriceQuoteService.
        FreelancerPriceQuoteUseCase.QuoteInput input = new FreelancerPriceQuoteUseCase.QuoteInput(
                distanceKm, packet.weightKg(), packet.volumeM3(), packet.fragile(), packet.perishable(),
                null, null, need != null ? need.getPickupDeadline() : null,
                need != null ? need.getPaymentMethod() : null, null);

        Mono<CourierCardService.Card> cardMono = cardOf(freelancer, freelancerId).cache();

        // Lot C-54 — sans politique personnelle active, la grille plateforme de SON véhicule.
        Mono<FreelancerPriceQuoteUseCase.Quote> quoteMono = cardMono
                .flatMap(card -> freelancerPriceQuoteUseCase
                        .quote(List.of(pricingId, identityId, freelancerId), input, card.vehicleType()))
                .onErrorResume(e -> {
                    // Un livreur dont le tarif est incalculable n'est pas proposé : mieux vaut
                    // l'omettre que montrer 0 ou un prix inventé.
                    log.error("Cannot price candidate {} for delivery need {} — candidate omitted",
                            freelancerId, need != null ? need.getId() : null, e);
                    return Mono.empty();
                });

        Mono<String[]> nameMono = gofpUserRepository.findByCoreUserId(identityId)
                .map(u -> new String[]{
                        (u.getFirstName() != null && !u.getFirstName().isBlank()) ? u.getFirstName() : "Freelancer",
                        u.getLastName() != null ? u.getLastName() : "",
                        u.getPhone()})
                .defaultIfEmpty(new String[]{"Freelancer", "", null})
                .onErrorReturn(new String[]{"Freelancer", "", null});

        return Mono.zip(quoteMono, nameMono, courierStatus(freelancer, freelancerId), cardMono)
                .map(t -> FreelancerCandidateDTO.builder()
                        .freelancerId(freelancerId)
                        .firstName(t.getT2()[0])
                        .lastName(t.getT2()[1])
                        .phone(t.getT2().length > 2 ? t.getT2()[2] : null)
                        .rating(candidate.getRating())
                        .estimatedPrice(t.getT1().totalPrice())
                        .priceBreakdown(t.getT1().breakdown())
                        .status(t.getT3())
                        .vehicleType(t.getT4().vehicleType())
                        .vehiclePlate(t.getT4().vehiclePlate())
                        .vehiclePhotoUrls(t.getT4().vehiclePhotoUrls())
                        .deliveredCount(t.getT4().deliveredCount())
                        .badge(t.getT4().badge())
                        .build());
    }

    /**
     * Lot C-54 — véhicule demandé par l'expéditeur, dans le vocabulaire du core. Absent, vide,
     * {@code ANY} ou « peu importe » = tous ; une valeur inconnue est refusée (400) plutôt qu'ignorée.
     */
    static String requiredVehicleOf(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.equals("any") || v.equals("peu importe") || v.equals("peu_importe")) return null;
        String canonical = com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleCategory
                .canonicalName(raw);
        if (canonical == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "requiredVehicleType must be one of moto, tricycle, taxi, camion, bus");
        }
        return canonical;
    }

    /** Coordonnées d'une adresse ; {@code (0,0)} si l'adresse ou ses coordonnées sont absentes. */
    private Mono<GeoPoint> loadCoordinates(UUID addressId) {
        if (addressId == null) {
            return Mono.just(GeoPoint.ORIGIN);
        }
        return addressUseCase.getAddressById(addressId)
                .map(dto -> {
                    Address address = dto.getAddress();
                    if (address == null) {
                        return GeoPoint.ORIGIN;
                    }
                    return address.getCoordinates()
                            .map(c -> new GeoPoint(c.getLatitude(), c.getLongitude()))
                            .orElse(GeoPoint.ORIGIN);
                })
                .defaultIfEmpty(GeoPoint.ORIGIN)
                .onErrorResume(e -> {
                    log.warn("Address {} lookup failed — falling back to (0,0)", addressId, e);
                    return Mono.just(GeoPoint.ORIGIN);
                });
    }

    /**
     * Métriques du colis, lues dans {@code tnt-delivery-core} (via {@link ParcelPort}). Unités du
     * cœur : {@code weightKg} en kg, dimensions en cm — d'où la conversion en m³ par 1e6.
     */
    private Mono<PacketMetrics> loadPacketMetrics(UUID packetId) {
        if (packetId == null || parcels == null) {
            return Mono.just(PacketMetrics.EMPTY);
        }
        return parcels.find(packetId)
                .map(p -> new PacketMetrics(
                        orDefault(p.weightKg(), 0.0),
                        (orDefault(p.lengthCm(), 0.0) * orDefault(p.widthCm(), 0.0) * orDefault(p.heightCm(), 0.0))
                                / 1_000_000.0,
                        Boolean.TRUE.equals(p.fragile()),
                        Boolean.TRUE.equals(p.perishable())))
                .defaultIfEmpty(PacketMetrics.EMPTY)
                .onErrorResume(e -> {
                    log.warn("Packet {} lookup failed — pricing without packet metrics", packetId, e);
                    return Mono.just(PacketMetrics.EMPTY);
                });
    }

    /** Latitude/longitude déjà résolues, jamais {@code null}. */
    private record GeoPoint(double lat, double lon) {
        private static final GeoPoint ORIGIN = new GeoPoint(0.0, 0.0);
    }

    /** Poids (kg), volume (m³) et suppléments applicables du colis. */
    private record PacketMetrics(double weightKg, double volumeM3, boolean fragile, boolean perishable) {
        private static final PacketMetrics EMPTY = new PacketMetrics(0.0, 0.0, false, false);
    }

    private static double orDefault(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    /**
     * Réponse enrichie : les deux adresses et le colis résolus <em>en une passe</em>.
     *
     * <p>Les trois lectures partent en parallèle sous un {@link Mono#zip} plutôt qu'en
     * {@code flatMap} chaînés : la latence d'un besoin est celle de la plus lente des trois,
     * pas leur somme. Les identifiants sont déjà portés par le besoin, aucun aller-retour
     * supplémentaire n'est nécessaire pour les découvrir.
     *
     * <p><strong>Court-circuit sur {@code null}.</strong> Un identifiant absent ne déclenche
     * aucune requête — c'est ce qui garde les besoins sans colis (le colis est optionnel à la
     * création) au même coût qu'avant.
     *
     * <p><strong>Portée honnête.</strong> Sur les routes qui renvoient un {@code Flux}, cela
     * reste 3 lectures <em>par besoin</em> : le zip supprime la sérialisation à l'intérieur d'un
     * besoin, pas la multiplication entre besoins. Un vrai chargement par lot supposerait un
     * {@code IN (...)} sur {@code addresses} et {@code packets}, donc une méthode de dépôt qui
     * n'existe pas encore. Voir {@link #enrichAll(Flux)} pour ce qui est fait en attendant.
     */
    private Mono<DeliveryNeedResponseDTO> enrich(DeliveryNeed need) {
        return Mono.zip(
                        loadAddress(need.getPickupAddressId()),
                        loadAddress(need.getDeliveryAddressId()),
                        loadPacket(need.getPacketId()))
                .map(ctx -> {
                    DeliveryNeedResponseDTO dto = mapToResponse(need);
                    dto.setPickupAddress(ctx.getT1().orElse(null));
                    dto.setDeliveryAddress(ctx.getT2().orElse(null));
                    dto.setPacket(ctx.getT3().orElse(null));
                    return dto;
                })
                .flatMap(dto -> needAnnouncements == null ? Mono.just(dto)
                        : needAnnouncements.announcementIdOf(need.getId())
                                .doOnNext(dto::setAnnouncementId)
                                .onErrorResume(e -> Mono.empty())
                                .thenReturn(dto))
                .flatMap(this::withAgreedPrice);
    }

    /**
     * Lot C-56 — livreur élu : la livraison et SON prix (le tarif figé à l'élection), pour que le
     * détail et Activités n'affichent plus « À fixer avec le livreur ».
     */
    private Mono<DeliveryNeedResponseDTO> withAgreedPrice(DeliveryNeedResponseDTO dto) {
        Mono<DeliveryNeedResponseDTO> named = dto.getAssignedFreelancerId() == null ? Mono.just(dto)
                : courierNameOf(dto.getAssignedFreelancerId()).doOnNext(dto::setAssignedCourierName).thenReturn(dto);
        if (deliveries == null || dto.getAssignedFreelancerId() == null && dto.getStatus() == com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus.PENDING) {
            return named;
        }
        return named.then(deliveries.findByDeliveryNeedId(dto.getId()))
                .doOnNext(d -> {
                    if (dto.getDeliveryId() == null) dto.setDeliveryId(d.getId());
                    if (d.getTarif() != null && d.getTarif() > 0) dto.setAgreedPrice(d.getTarif());
                })
                .onErrorResume(e -> Mono.empty())
                .thenReturn(dto);
    }

    /**
     * Enrichit un flux de besoins avec {@link Flux#flatMapSequential} : les besoins sont
     * enrichis concurremment mais réémis dans l'ordre d'arrivée. Un {@code concatMap} aurait
     * sérialisé les lectures (N × latence), un {@code flatMap} aurait rendu l'ordre de la liste
     * non déterministe d'un appel à l'autre — inacceptable pour une liste affichée.
     */
    private Flux<DeliveryNeedResponseDTO> enrichAll(Flux<DeliveryNeed> needs) {
        return needs.flatMapSequential(this::enrich);
    }

    /**
     * Adresse résolue, ou {@link Optional#empty()} si l'identifiant est {@code null}, si la ligne
     * n'existe plus, ou si la lecture échoue. L'enrichissement ne doit jamais faire échouer la
     * lecture du besoin lui-même : le contrat historique (les identifiants) reste servi.
     */
    private Mono<Optional<AddressDTO>> loadAddress(UUID addressId) {
        if (addressId == null) {
            return Mono.just(Optional.empty());
        }
        return addressUseCase.getAddressById(addressId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(e -> {
                    log.warn("Address {} lookup failed — response served without the full address",
                            addressId, e);
                    return Mono.just(Optional.empty());
                });
    }

    /**
     * Colis résolu dans {@code tnt-delivery-core} (via {@link ParcelPort}) : il n'existe plus de table
     * {@code packets} côté Go, et aucun endpoint {@code GET /api/packets/{id}} — sans cette lecture, le
     * colis est invisible pour tout consommateur. Les noms du DTO sont ceux de l'API
     * ({@code fragile}/{@code photoPacket}).
     */
    private Mono<Optional<PacketDTO>> loadPacket(UUID packetId) {
        if (packetId == null || parcels == null) {
            return Mono.just(Optional.empty());
        }
        return parcels.find(packetId)
                .map(p -> Optional.of(toPacketDTO(p)))
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(e -> {
                    log.warn("Packet {} lookup failed — response served without the full packet",
                            packetId, e);
                    return Mono.just(Optional.empty());
                });
    }

    private DeliveryNeedResponseDTO mapToResponse(DeliveryNeed need) {
        return DeliveryNeedResponseDTO.builder()
                .id(need.getId())
                .userId(need.getUserId())
                .packetId(need.getPacketId())
                .pickupAddressId(need.getPickupAddressId())
                .deliveryAddressId(need.getDeliveryAddressId())
                .title(need.getTitle())
                .description(need.getDescription())
                .status(need.getStatus())
                .outcome(need.getOutcome())
                .duration(need.getDuration())
                .signatureUrl(need.getSignatureUrl())
                .paymentMethod(need.getPaymentMethod())
                .transportMethod(need.getTransportMethod())
                .requiredVehicleType(need.getRequiredVehicleType())
                .distance(need.getDistance())
                .deliveryId(need.getDeliveryId())
                .assignedFreelancerId(need.getAssignedFreelancerId())
                .createdAt(need.getCreatedAt())
                .updatedAt(need.getUpdatedAt())
                .pickupDeadline(need.getPickupDeadline())
                .senderFirstName(need.getSenderFirstName())
                .senderLastName(need.getSenderLastName())
                .senderPhone(need.getSenderPhone())
                .recipientFirstName(need.getRecipientFirstName())
                .recipientLastName(need.getRecipientLastName())
                .recipientPhone(need.getRecipientPhone())
                .build();
    }

    /**
     * Lot C-56 — nom réel d'un livreur (profil gofp → utilisateur), vide s'il n'en a pas encore :
     * jamais « Freelancer » quand un nom existe, jamais un nom inventé quand il n'existe pas.
     */
    public Mono<String> courierNameOf(UUID gofpFreelancerId) {
        if (gofpFreelancerId == null) return Mono.empty();
        return gofpFreelancerRepository.findById(gofpFreelancerId)
                .switchIfEmpty(Mono.defer(() -> gofpFreelancerRepository.findByCoreFreelancerId(gofpFreelancerId)))
                .filter(f -> f.getCoreUserId() != null)
                .flatMap(f -> gofpUserRepository.findByCoreUserId(f.getCoreUserId()))
                .flatMap(u -> Mono.justOrEmpty(NeedAnnouncementService.fullName(u.getFirstName(), u.getLastName())))
                .onErrorResume(e -> Mono.empty());
    }

    /** Valeur saisie, rognée ; vide = absente (jamais une valeur inventée). */
    static String clean(String raw) {
        if (raw == null) return null;
        String v = raw.strip();
        return v.isEmpty() ? null : v.substring(0, Math.min(v.length(), 120));
    }
}
