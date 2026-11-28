package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementExtras;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
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
 * <p>Le lien besoin → annonce est la référence d'origine de l'annonce du core
 * ({@code origin_ref_type = DELIVERY_NEED}, {@code origin_ref_id} = id du besoin, unique). Un seul
 * état de vérité : l'annonce n'est que la face « marché » du besoin, dont le statut est dérivé
 * de l'annonce et de la livraison (rien à synchroniser) ; supprimer ou assigner directement le
 * besoin retire l'annonce.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NeedAnnouncementService {

    private final IDeliveryAnnouncementPort deliveryAnnouncementPort;
    private final TenantContextHolder tenantContextHolder;

    /**
     * Colis tel que l'annonce le décrit ; {@code null} pour un champ inconnu (valeurs neutres
     * appliquées, comme {@code AnnouncementApplicationService.toPublishCommand}).
     */
    public record ParcelSpec(Double weightKg, Double widthCm, Double heightCm, Double lengthCm,
                             Boolean fragile, Boolean perishable, String description) {
    }

    /**
     * Publie le besoin en annonce {@code QUOTE_REQUEST} du cœur (aucun séquestre à la publication : le
     * prix vient des réponses des livreurs), reliée au besoin par sa référence d'origine. Les erreurs
     * remontent : appelé dans la transaction de création du besoin, un échec annule le besoin.
     *
     * <p>Idempotent (lot L2) : une annonce déjà publiée pour ce besoin (rejeu) est rendue telle quelle
     * au lieu d'une seconde publication que l'index unique {@code uq_tnt_ann_origin_ref} refuserait ; si
     * une publication concurrente gagne la course, l'annonce gagnante est relue.
     *
     * @return l'identifiant de l'annonce du besoin
     */
    public Mono<UUID> publishFor(DeliveryNeed need, Address pickup, Address delivery, ParcelSpec parcel) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> existingFor(tenantId, need.getId())
                        .doOnNext(a -> log.info("DeliveryNeed {} already published as announcement {}: reused",
                                need.getId(), a.id()))
                        .switchIfEmpty(Mono.defer(() -> deliveryAnnouncementPort
                                .publish(toCommand(tenantId, need, pickup, delivery, parcel))
                                .doOnNext(published -> log.info(
                                        "DeliveryNeed {} published as QUOTE_REQUEST announcement {}",
                                        need.getId(), published.id()))
                                .onErrorResume(org.springframework.dao.DataIntegrityViolationException.class,
                                        e -> existingFor(tenantId, need.getId())
                                                .switchIfEmpty(Mono.error(e))))))
                .map(AnnouncementSnapshot::id);
    }

    private Mono<AnnouncementSnapshot> existingFor(UUID tenantId, UUID deliveryNeedId) {
        return deliveryAnnouncementPort.findByOrigin(tenantId, AnnouncementExtras.ORIGIN_DELIVERY_NEED, deliveryNeedId);
    }

    /** Besoin publié par cette annonce, ou vide. */
    public Mono<UUID> needIdOf(UUID announcementId) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, announcementId))
                .flatMap(a -> Mono.justOrEmpty(AnnouncementExtras.ORIGIN_DELIVERY_NEED.equals(a.extras().originRefType())
                        ? a.extras().originRefId() : null));
    }

    /** Annonce encore ouverte (PUBLISHED ou IN_NEGOTIATION) publiée pour ce besoin, ou vide. */
    public Mono<AnnouncementSnapshot> openAnnouncementOf(UUID deliveryNeedId) {
        return announcementOf(deliveryNeedId)
                .filter(a -> a.status() == AnnouncementStatus.PUBLISHED
                        || a.status() == AnnouncementStatus.IN_NEGOTIATION);
    }

    private Mono<AnnouncementSnapshot> announcementOf(UUID deliveryNeedId) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryAnnouncementPort.findByOrigin(
                        tenantId, AnnouncementExtras.ORIGIN_DELIVERY_NEED, deliveryNeedId));
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

    /** Lot C-56 — tenant où l'annonce a été publiée (vide si inconnu : le tenant de l'appelant s'applique). */
    public Mono<UUID> tenantOf(UUID announcementId) {
        Mono<UUID> tenant = deliveryAnnouncementPort.tenantOf(announcementId);
        return tenant == null ? Mono.empty() : tenant.onErrorResume(e -> Mono.empty());
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
        return announcementOf(deliveryNeedId).map(AnnouncementSnapshot::id);
    }

    /**
     * Le besoin n'attend plus de livreur (supprimé, ou assigné directement sans passer par
     * l'annonce) : son annonce encore ouverte est annulée dans tnt-delivery-core.
     */
    public Mono<Void> withdrawFor(UUID deliveryNeedId) {
        return openAnnouncementOf(deliveryNeedId)
                .flatMap(a -> tenantContextHolder.currentTenantId()
                        .flatMap(tenantId -> deliveryAnnouncementPort.cancel(tenantId, a.id(), a.clientId()))
                        .doOnSuccess(x -> log.info("DeliveryNeed {} no longer open → announcement {} CANCELLED",
                                deliveryNeedId, a.id())))
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
        // Lot C-56 — contacts du besoin ; tnt-delivery-core les exige non vides : « — » si vraiment
        // absents, jamais un nom ou un numéro inventé.
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
                orDash(fullName(need.getRecipientFirstName(), need.getRecipientLastName())),
                orDash(need.getRecipientPhone()),
                null,
                new AnnouncementExtras(fullName(need.getSenderFirstName(), need.getSenderLastName()),
                        blankToNull(need.getSenderPhone()), null, null, null, need.getRequiredVehicleType(),
                        need.getPaymentMethod(), need.getTransportMethod(),
                        need.getDistance() != null ? java.math.BigDecimal.valueOf(need.getDistance()) : null,
                        null, null, null, null, AnnouncementExtras.ORIGIN_DELIVERY_NEED, need.getId()))
                // The goods registered with the need keep their parcel id in the announcement and the delivery.
                .withParcelId(need.getPacketId());
    }

    /** Lot C-56 — valeur affichée quand un contact manque vraiment. */
    public static final String ABSENT = "—";

    static String fullName(String first, String last) {
        String f = first == null ? "" : first.strip();
        String l = last == null ? "" : last.strip();
        String n = (f + " " + l).strip();
        return n.isEmpty() ? null : n;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }

    private static String orDash(String v) {
        return v == null || v.isBlank() ? ABSENT : v;
    }
}
