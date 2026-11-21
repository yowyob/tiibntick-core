package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Un besoin de livraison Go est publié comme annonce {@code QUOTE_REQUEST}, que les
 * livreurs de la zone voient et à laquelle ils répondent.
 *
 * <p>Le lien besoin → annonce est porté par le core, dans le miroir local gofp de l'annonce
 * ({@code announcements.delivery_need_id}, unique). Un seul état de vérité : l'annonce n'est que
 * la face « marché » du besoin — l'assigner assigne le besoin, assigner ou supprimer le besoin
 * retire l'annonce.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NeedAnnouncementService {

    private final IDeliveryAnnouncementPort deliveryAnnouncementPort;
    private final TenantContextHolder tenantContextHolder;
    private final IAnnouncementRepository announcementRepository;
    private final IDeliveryNeedRepository deliveryNeedRepository;
    private final GofpFreelancerRepository gofpFreelancerRepository;

    /**
     * Publie le besoin en annonce {@code QUOTE_REQUEST} (aucun séquestre à la publication : le prix
     * vient des réponses des livreurs) et écrit le miroir local qui porte le lien besoin → annonce.
     * Les erreurs remontent : appelé dans la transaction de création du besoin, un échec annule
     * le besoin — jamais de besoin « publié » sans annonce, ni d'annonce orpheline.
     *
     * @return l'identifiant de l'annonce publiée
     */
    /**
     * Colis tel que l'annonce le décrit ; {@code null} pour un champ inconnu (valeurs neutres
     * appliquées, comme {@code AnnouncementApplicationService.toPublishCommand}).
     */
    public record ParcelSpec(Double weightKg, Double widthCm, Double heightCm, Double lengthCm,
                             Boolean fragile, Boolean perishable, String description) {
    }

    public Mono<UUID> publishFor(DeliveryNeed need, Address pickup, Address delivery, ParcelSpec parcel) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.publish(toCommand(tenantId, need, pickup, delivery, parcel)))
                .flatMap(published -> {
                    Announcement local = new Announcement();
                    local.setId(published.id());
                    local.setClientId(published.clientId());
                    local.setTitle(published.title());
                    local.setStatus(AnnouncementStatus.PUBLISHED);
                    local.setPaymentMethod(need.getPaymentMethod());
                    local.setTransportMethod(need.getTransportMethod());
                    local.setRequiredVehicleType(need.getRequiredVehicleType()); // lot C-54
                    local.setDistance(need.getDistance());
                    local.setCurrency(published.currency());
                    local.setDeliveryNeedId(need.getId());
                    log.info("DeliveryNeed {} published as QUOTE_REQUEST announcement {}", need.getId(), published.id());
                    return announcementRepository.save(local).thenReturn(published.id());
                });
    }

    /**
     * L'expéditeur a retenu une réponse sur l'annonce : si elle publie un besoin, le besoin passe
     * dans le même état que par {@code DeliveryNeedApplicationService.assignFreelancer}.
     */
    public Mono<Void> syncAssignment(UUID announcementId, UUID freelancerId) {
        if (freelancerId == null) return Mono.empty();
        return needIdOf(announcementId)
                .flatMap(deliveryNeedRepository::findById)
                .flatMap(need -> assignableIdOf(freelancerId)
                        .map(java.util.Optional::of)
                        .defaultIfEmpty(java.util.Optional.empty())
                        .flatMap(gofpId -> {
                            // delivery_needs.assigned_freelancer_id references gofp_freelancers(id)
                            // (changeset 070): a response carries the responder's profile id, which
                            // may live in another identity space — translate it, never store it raw.
                            gofpId.ifPresentOrElse(need::setAssignedFreelancerId, () -> log.warn(
                                    "Announcement {} assigned to {} with no gofp freelancer row — "
                                            + "DeliveryNeed {} ASSIGNED without assigned_freelancer_id",
                                    announcementId, freelancerId, need.getId()));
                            need.setStatus(DeliveryNeedStatus.ASSIGNED);
                            need.setUpdatedAt(Instant.now());
                            log.info("Announcement {} assigned → DeliveryNeed {} ASSIGNED to {}",
                                    announcementId, need.getId(), gofpId.orElse(null));
                            return deliveryNeedRepository.save(need);
                        }))
                .then();
    }

    /** Same resolution as {@code FreelancerQuotaService}: gofp id first, then the core freelancer id. */
    private Mono<UUID> assignableIdOf(UUID freelancerId) {
        return gofpFreelancerRepository.findById(freelancerId)
                .switchIfEmpty(Mono.defer(() -> gofpFreelancerRepository.findByCoreFreelancerId(freelancerId)))
                .map(GofpFreelancer::getId);
    }

    /** Besoin publié par cette annonce, ou vide. */
    public Mono<UUID> needIdOf(UUID announcementId) {
        return announcementRepository.findById(announcementId)
                .flatMap(a -> Mono.justOrEmpty(a.getDeliveryNeedId()));
    }

    /** Annonce encore ouverte (PUBLISHED ou IN_NEGOTIATION) publiée pour ce besoin, ou vide. */
    public Mono<Announcement> openAnnouncementOf(UUID deliveryNeedId) {
        return announcementRepository.findByDeliveryNeedId(deliveryNeedId)
                .filter(a -> a.getStatus() == AnnouncementStatus.PUBLISHED
                        || a.getStatus() == AnnouncementStatus.IN_NEGOTIATION);
    }

    /**
     * Réponse du livreur {@code responderId} sur l'annonce, au prix {@code serverPrice} calculé par
     * le serveur (lot C-46) : créée si absente ; réutilisée si elle existe au même prix ; refusée
     * (409) si le livreur a déjà proposé un autre prix — le tarif ne vient jamais d'une requête.
     */
    public Mono<UUID> responseAt(UUID announcementId, UUID responderId, java.math.BigDecimal serverPrice,
                                 String currency) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, announcementId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "Announcement not found: " + announcementId)))
                        .flatMap(a -> {
                            java.util.Optional<com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot> existing =
                                    a.responses().stream()
                                            .filter(r -> responderId.equals(r.deliveryPersonId()))
                                            .findFirst();
                            if (existing.isPresent()) {
                                java.math.BigDecimal proposed = existing.get().proposedPrice();
                                if (proposed == null || proposed.compareTo(serverPrice) != 0) {
                                    return Mono.error(new org.springframework.web.server.ResponseStatusException(
                                            org.springframework.http.HttpStatus.CONFLICT,
                                            "This courier already proposed another price: choose them among the applicants"));
                                }
                                return Mono.just(existing.get().id());
                            }
                            return deliveryAnnouncementPort.respond(
                                            new com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.RespondToAnnouncementPortCommand(
                                                    tenantId, announcementId, responderId,
                                                    Instant.now().plusSeconds(1800), null, serverPrice, currency))
                                    .flatMap(saved -> Mono.justOrEmpty(saved.responses().stream()
                                            .filter(r -> responderId.equals(r.deliveryPersonId()))
                                            .map(com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot::id)
                                            .findFirst()))
                                    .switchIfEmpty(Mono.error(new IllegalStateException(
                                            "Response of " + responderId + " not recorded on " + announcementId)));
                        }));
    }

    /** Lot C-52 — l'un de ces livreurs a-t-il déjà répondu (postulé) sur cette annonce ? */
    public Mono<Boolean> hasResponseFrom(UUID announcementId, java.util.Collection<UUID> responderIds) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, announcementId))
                .map(a -> a.responses().stream().anyMatch(r -> responderIds.contains(r.deliveryPersonId())))
                .defaultIfEmpty(false);
    }

    /** Annonce publiée pour ce besoin, ou vide. */
    public Mono<UUID> announcementIdOf(UUID deliveryNeedId) {
        return announcementRepository.findByDeliveryNeedId(deliveryNeedId).map(Announcement::getId);
    }

    /**
     * Le besoin n'attend plus de livreur (supprimé, ou assigné directement sans passer par
     * l'annonce) : son annonce encore ouverte est annulée dans tnt-delivery-core et dans le miroir.
     */
    public Mono<Void> withdrawFor(UUID deliveryNeedId) {
        return announcementRepository.findByDeliveryNeedId(deliveryNeedId)
                .filter(a -> a.getStatus() == AnnouncementStatus.PUBLISHED
                        || a.getStatus() == AnnouncementStatus.IN_NEGOTIATION)
                .flatMap(a -> tenantContextHolder.currentTenantId()
                        .flatMap(tenantId -> deliveryAnnouncementPort.cancel(tenantId, a.getId(), a.getClientId()))
                        .then(Mono.defer(() -> {
                            a.setStatus(AnnouncementStatus.CANCELLED);
                            log.info("DeliveryNeed {} no longer open → announcement {} CANCELLED",
                                    deliveryNeedId, a.getId());
                            return announcementRepository.save(a);
                        })))
                .then();
    }

    private static PublishAnnouncementPortCommand toCommand(UUID tenantId, DeliveryNeed need,
                                                            Address pickup, Address delivery,
                                                            ParcelSpec parcel) {
        ParcelSpec p = parcel != null ? parcel : new ParcelSpec(null, null, null, null, null, null, null);
        double weight = p.weightKg() != null ? p.weightKg() : 1.0;
        double width = p.widthCm() != null ? p.widthCm() : 10.0;
        double height = p.heightCm() != null ? p.heightCm() : 10.0;
        double length = p.lengthCm() != null ? p.lengthCm() : 10.0;
        boolean fragile = Boolean.TRUE.equals(p.fragile());
        boolean perishable = Boolean.TRUE.equals(p.perishable());
        String packageDescription = p.description() != null
                ? p.description()
                : (need.getDescription() != null ? need.getDescription() : "Package");
        // Contacts : le besoin n'en porte pas — mêmes valeurs neutres que
        // AnnouncementApplicationService.toPublishCommand, que tnt-delivery-core exige non nulles.
        return new PublishAnnouncementPortCommand(
                tenantId,
                need.getUserId(),
                need.getTitle(),
                need.getDescription(),
                null,
                "XAF",
                AnnouncementSnapshot.PRICING_MODE_QUOTE_REQUEST,
                weight, width, height, length,
                fragile, perishable, packageDescription,
                pickup,
                delivery,
                "Recipient",
                "+237000000000");
    }
}
