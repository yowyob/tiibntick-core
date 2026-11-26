package com.yowyob.tiibntick.core.gofreelancer.application.service;

import static com.yowyob.tiibntick.core.gofreelancer.application.service.InAppNotificationService.params;

import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Lot C-36 — the P1 events of the delivery journey, turned into in-app notifications for the right
 * people. Each method is called once the action has succeeded; each is best-effort (never fails the
 * action) and idempotent (event key per recipient).
 *
 * <p>Recipients are user ids: the sender is the need's {@code userId}; the courier is the
 * {@code coreUserId} of their gofp profile; the relay is the {@code coreUserId} of the freelancer
 * whose {@code coreFreelancerId} the relay point carries.
 *
 * <p>The French text is stored; {@code params} let the app render it in the user's language.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InAppEvents {

    public static final String LINK_SHIPMENT = "SHIPMENT";
    public static final String LINK_COURSE = "COURSE";
    public static final String LINK_RELAY_PARCEL = "RELAY_PARCEL";

    private final InAppNotificationService inbox;
    private final IDeliveryNeedRepository needs;
    private final IAnnouncementRepository announcements;
    private final GofpFreelancerRepository freelancers;
    private final GofpRelayPointRepository relayPoints;
    private final GofpUserRepository users;
    private final AddressUseCase addresses;

    /** A courier applied to the sender's announcement. */
    public Mono<Void> candidateApplied(UUID announcementId, UUID responderId, BigDecimal price) {
        return safe(announcements.findById(announcementId)
                .flatMap(a -> Mono.justOrEmpty(a.getDeliveryNeedId()))
                .flatMap(needs::findById)
                .flatMap(need -> courier(responderId).flatMap(this::fullName).defaultIfEmpty("")
                        .flatMap(name -> {
                            String who = name.isBlank() ? "un livreur" : name;
                            String amount = xaf(price);
                            return inbox.emit(need.getUserId(), NotificationType.CANDIDATE_APPLIED,
                                    "applied:" + announcementId + ":" + responderId,
                                    "Nouveau candidat",
                                    "Nouveau candidat : " + who + (amount != null ? ", " + amount + " XAF" : ""),
                                    params("name", name, "price", amount, "ref", ref(need.getId())),
                                    LINK_SHIPMENT, need.getId());
                        })));
    }

    /** The sender invited (assigned directly) a courier, who has to accept. */
    public Mono<Void> courseOffered(NeedInvitation inv) {
        return safe(needs.findById(inv.getDeliveryNeedId())
                .flatMap(need -> Mono.zip(place(need.getPickupAddressId()), place(need.getDeliveryAddressId()),
                                freelancers.findById(inv.getFreelancerId()))
                        .flatMap(t -> {
                            String from = t.getT1();
                            String to = t.getT2();
                            String amount = inv.getEstimatedPrice() != null
                                    ? xaf(BigDecimal.valueOf(inv.getEstimatedPrice())) : null;
                            return inbox.emit(t.getT3().getCoreUserId(), NotificationType.COURSE_OFFERED,
                                    "offered:" + inv.getId(),
                                    "Course proposée",
                                    "Course proposée pour toi : " + from + " → " + to
                                            + (amount != null ? ", " + amount + " XAF" : ""),
                                    params("from", from, "to", to, "price", amount, "ref", ref(need.getId())),
                                    LINK_COURSE, need.getId());
                        })));
    }

    /** The courier is elected (invitation accepted, or candidate chosen by the sender). */
    public Mono<Void> courierAssigned(DeliveryNeed need, GofpFreelancer courier) {
        String key = "assigned:" + need.getId() + ":" + courier.getId();
        String ref = ref(need.getId());
        return safe(fullName(courier).defaultIfEmpty("").flatMap(name -> inbox.emit(need.getUserId(),
                        NotificationType.COURSE_ACCEPTED, key, "Livreur trouvé",
                        (name.isBlank() ? "Le livreur" : name) + " a accepté ta course",
                        params("name", name, "ref", ref), LINK_SHIPMENT, need.getId()))
                .then(Mono.defer(() -> inbox.emit(courier.getCoreUserId(), NotificationType.COURIER_SELECTED,
                        key, "Tu as été choisi", "Tu as été choisi pour la course " + ref,
                        params("ref", ref), LINK_COURSE, need.getId()))));
    }

    /** An invitation was declined, or expired unanswered: the sender has to choose someone else. */
    public Mono<Void> invitationClosed(NeedInvitation inv) {
        boolean declined = NeedInvitation.DECLINED.equals(inv.getStatus());
        boolean expired = NeedInvitation.EXPIRED.equals(inv.getStatus());
        if (!declined && !expired) {
            return Mono.empty();
        }
        return safe(needs.findById(inv.getDeliveryNeedId())
                .flatMap(need -> courier(inv.getFreelancerId()).flatMap(this::fullName).defaultIfEmpty("")
                        .flatMap(name -> {
                            String who = name.isBlank() ? "Le livreur" : name;
                            String text = declined
                                    ? who + " a décliné : choisis un autre livreur"
                                    : "L'invitation a expiré : choisis un autre livreur";
                            return inbox.emit(need.getUserId(),
                                    declined ? NotificationType.INVITATION_DECLINED : NotificationType.INVITATION_EXPIRED,
                                    "invitation-closed:" + inv.getId(),
                                    declined ? "Course déclinée" : "Invitation expirée", text,
                                    params("name", name, "ref", ref(need.getId())), LINK_SHIPMENT, need.getId());
                        })));
    }

    /** The sender chose this relay point for the parcel. */
    public Mono<Void> relayChosen(DeliveryNeed need) {
        if (need.getTargetRelayPointId() == null) {
            return Mono.empty();
        }
        return safe(relay(need.getTargetRelayPointId())
                .flatMap(rp -> relayOwner(rp).flatMap(owner -> inbox.emit(owner, NotificationType.RELAY_PARCEL_INCOMING,
                        "relay-chosen:" + need.getId() + ":" + rp.getId(),
                        "Un colis va arriver", "Un colis va arriver : réf " + ref(need.getId()),
                        params("ref", ref(need.getId())), LINK_RELAY_PARCEL, need.getId()))));
    }

    /** The courier picked the parcel up (pickup code checked). */
    public Mono<Void> pickedUp(UUID needId) {
        return safe(needs.findById(needId).flatMap(need -> inbox.emit(need.getUserId(),
                NotificationType.PARCEL_PICKED_UP, "picked-up:" + needId,
                "Colis récupéré", "Ton colis est en route",
                params("ref", ref(needId)), LINK_SHIPMENT, needId)));
    }

    /** The parcel was deposited at the relay point: sender and relay are told. */
    public Mono<Void> depositedAtRelay(UUID needId, UUID relayPointOrHubId) {
        return safe(needs.findById(needId).flatMap(need -> relay(relayPointOrHubId).flatMap(rp -> {
            String relayName = rp.getName() != null && !rp.getName().isBlank() ? rp.getName() : "";
            Mono<Void> toSender = inbox.emit(need.getUserId(), NotificationType.PARCEL_AT_RELAY,
                    "at-relay:" + needId, "Colis au relais",
                    "Ton colis est au relais" + (relayName.isBlank() ? "" : " " + relayName),
                    params("relay", relayName, "ref", ref(needId)), LINK_SHIPMENT, needId);
            Mono<Void> toRelay = relayOwner(rp).flatMap(owner -> inbox.emit(owner,
                    NotificationType.RELAY_PARCEL_RECEIVED, "at-relay:" + needId, "Colis reçu",
                    "Colis reçu, en attente de retrait", params("ref", ref(needId)),
                    LINK_RELAY_PARCEL, needId));
            return toSender.then(toRelay);
        })));
    }

    /**
     * The parcel reached its recipient: sender and courier are told, and the relay when it was
     * collected there ({@code relayPointOrHubId} non null).
     */
    public Mono<Void> handedOver(UUID needId, UUID courierFreelancerId, UUID relayPointOrHubId) {
        return safe(needs.findById(needId).flatMap(need -> {
            String key = "handed-over:" + needId;
            Mono<Void> toSender = inbox.emit(need.getUserId(), NotificationType.PARCEL_HANDED_OVER, key,
                    "Colis remis", "Colis remis", params("ref", ref(needId)), LINK_SHIPMENT, needId);
            Mono<Void> toCourier = courier(courierFreelancerId).flatMap(c -> inbox.emit(c.getCoreUserId(),
                    NotificationType.PARCEL_HANDED_OVER, key, "Colis remis", "Colis remis",
                    params("ref", ref(needId)), LINK_COURSE, needId));
            Mono<Void> toRelay = relayPointOrHubId == null ? Mono.empty()
                    : relay(relayPointOrHubId).flatMap(this::relayOwner).flatMap(owner -> inbox.emit(owner,
                            NotificationType.PARCEL_HANDED_OVER, key, "Colis remis", "Colis remis",
                            params("ref", ref(needId)), LINK_RELAY_PARCEL, needId));
            return toSender.then(toCourier).then(toRelay);
        }));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Short public reference of a delivery need (first 8 characters of its id, upper case). */
    static String ref(UUID needId) {
        return needId == null ? "" : needId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    static String xaf(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return null;
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private Mono<GofpFreelancer> courier(UUID freelancerOrCoreId) {
        if (freelancerOrCoreId == null) return Mono.empty();
        return freelancers.findById(freelancerOrCoreId)
                .switchIfEmpty(Mono.defer(() -> freelancers.findByCoreFreelancerId(freelancerOrCoreId)));
    }

    private Mono<GofpRelayPoint> relay(UUID relayPointOrHubId) {
        return relayPoints.findById(relayPointOrHubId)
                .switchIfEmpty(Mono.defer(() -> relayPoints.findByCoreRelayPointId(relayPointOrHubId)));
    }

    private Mono<UUID> relayOwner(GofpRelayPoint rp) {
        if (rp.getCoreFreelancerId() == null) return Mono.empty();
        return freelancers.findByCoreFreelancerId(rp.getCoreFreelancerId())
                .flatMap(f -> Mono.justOrEmpty(f.getCoreUserId()));
    }

    /** Commercial name when the profile has one (no lookup). */
    private static String nameOf(GofpFreelancer f) {
        return f.getCommercialName() != null ? f.getCommercialName().strip() : "";
    }

    private Mono<String> fullName(GofpFreelancer f) {
        if (f.getCoreUserId() == null) return Mono.justOrEmpty(blankToNull(nameOf(f)));
        return users.findByCoreUserId(f.getCoreUserId())
                .flatMap(u -> Mono.justOrEmpty(NeedAnnouncementService.fullName(u.getFirstName(), u.getLastName())))
                .switchIfEmpty(Mono.defer(() -> Mono.justOrEmpty(blankToNull(nameOf(f)))));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** « Quartier, Ville » of an address, or its city, or « ? ». */
    private Mono<String> place(UUID addressId) {
        if (addressId == null) return Mono.just("?");
        return addresses.getAddressById(addressId)
                .map(dto -> {
                    var a = dto.getAddress();
                    if (a == null) return "?";
                    String q = a.getQuarter().orElse(null);
                    String c = a.getCity();
                    String label = q != null && !q.isBlank() ? q + (c != null && !c.isBlank() ? ", " + c : "")
                            : (c != null && !c.isBlank() ? c : null);
                    return label != null ? label : "?";
                })
                .onErrorReturn("?")
                .defaultIfEmpty("?");
    }

    private static Mono<Void> safe(Mono<Void> work) {
        return work.onErrorResume(e -> {
            log.warn("In-app event not notified: {}", e.getMessage());
            return Mono.empty();
        });
    }
}
