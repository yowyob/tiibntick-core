package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * Lot C-46 — who may act on an announcement. Identities come from the caller's token, never from
 * a request body:
 * <ul>
 *   <li>the client is the caller ({@link #clientIdFor});</li>
 *   <li>a courier answers only as themself ({@link #courierIdFor});</li>
 *   <li>update and cancellation are for the client who owns the announcement, or staff
 *       ({@link #requireOwner});</li>
 *   <li>electing a response is for the owner or staff; a courier may only take a fixed-price
 *       announcement for themself — on a quote request the client chooses ({@link #requireCanElect}).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AnnouncementAccessGuard {

    private final IDeliveryAnnouncementPort deliveryAnnouncementPort;
    private final TenantContextHolder tenantContextHolder;
    private final GofpFreelancerRepository gofpFreelancerRepository;

    /**
     * Lot C-54b — read view of the core announcement (its origin reference links it to a delivery need,
     * lot L2; there is no local mirror any more) and delivery needs, to let
     * the owner of the linked need read the applications. Optional (setter): absent, only the
     * announcement's client and staff can.
     */
    private com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository announcementMirror;
    private com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository deliveryNeeds;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAnnouncementMirror(
            com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository announcementMirror) {
        this.announcementMirror = announcementMirror;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDeliveryNeeds(
            com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository deliveryNeeds) {
        this.deliveryNeeds = deliveryNeeds;
    }

    /** The client of a new announcement: the caller. A different id in the body is refused (staff excepted). */
    public Mono<UUID> clientIdFor(UUID requestedClientId, TntSecurityContext ctx) {
        return caller(ctx).flatMap(caller -> {
            if (requestedClientId == null || requestedClientId.equals(caller)) {
                return Mono.just(caller);
            }
            return DeliveryStatusApplicationService.isStaff(ctx)
                    ? Mono.just(requestedClientId)
                    : Mono.error(new AccessDeniedException("An announcement can only be published for yourself"));
        });
    }

    /** The courier answering: one of the caller's own courier ids. A different id in the body is refused. */
    public Mono<UUID> courierIdFor(UUID requestedFreelancerId, TntSecurityContext ctx) {
        return courierIdsOf(ctx).flatMap(ids -> {
            if (ids.isEmpty()) {
                return Mono.error(new AccessDeniedException("Only a courier can answer an announcement"));
            }
            if (requestedFreelancerId == null) {
                return Mono.just(ids.iterator().next());
            }
            return ids.contains(requestedFreelancerId)
                    ? Mono.just(requestedFreelancerId)
                    : Mono.error(new AccessDeniedException("A courier can only answer for themself"));
        });
    }

    /** Owner of the announcement or staff, else 403. */
    public Mono<AnnouncementSnapshot> requireOwner(UUID announcementId, TntSecurityContext ctx) {
        return caller(ctx).flatMap(caller -> snapshot(announcementId).flatMap(a -> {
            if (caller.equals(a.clientId()) || DeliveryStatusApplicationService.isStaff(ctx)) {
                return Mono.just(a);
            }
            return Mono.<AnnouncementSnapshot>error(new AccessDeniedException(
                    "Only the client who published this announcement can do this"));
        }));
    }

    /**
     * Lot C-54b — reading the applications of an announcement (courier phone, name, plate, vehicle
     * photos): the client who published it, the owner of the delivery need it was published for, or
     * staff. Anyone else → 403; unknown announcement → 404. Strict: not driven by
     * {@code tnt.gofp.ownership-guard.enabled}.
     */
    public Mono<AnnouncementSnapshot> requireCanReadApplications(UUID announcementId, TntSecurityContext ctx) {
        return caller(ctx).flatMap(caller -> snapshot(announcementId).flatMap(a -> {
            if (caller.equals(a.clientId()) || DeliveryStatusApplicationService.isStaff(ctx)) {
                return Mono.just(a);
            }
            return isOwnerOfLinkedNeed(announcementId, caller)
                    .flatMap(owner -> owner ? Mono.just(a) : Mono.<AnnouncementSnapshot>error(new AccessDeniedException(
                            "Only the client who published this announcement can read its applications")));
        }));
    }

    /**
     * Lot C-54b — the announcements a courier applied to: that courier (one of his ids) or staff, else 403.
     */
    public Mono<UUID> requireSelfCourierOrStaff(UUID freelancerId, TntSecurityContext ctx) {
        return caller(ctx).flatMap(caller -> {
            if (DeliveryStatusApplicationService.isStaff(ctx)) {
                return Mono.just(freelancerId);
            }
            return courierIdsOf(ctx).flatMap(ids -> ids.contains(freelancerId)
                    ? Mono.just(freelancerId)
                    : Mono.<UUID>error(new AccessDeniedException(
                            "A courier can only list the announcements he applied to")));
        });
    }

    private Mono<Boolean> isOwnerOfLinkedNeed(UUID announcementId, UUID caller) {
        if (announcementMirror == null || deliveryNeeds == null) return Mono.just(false);
        return announcementMirror.findById(announcementId)
                .flatMap(local -> local.getDeliveryNeedId() == null ? Mono.<Boolean>empty()
                        : deliveryNeeds.findById(local.getDeliveryNeedId())
                                .map(need -> caller.equals(need.getUserId())))
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    /**
     * Electing a response: the owner or staff; otherwise a courier taking a FIXED_PRICE
     * announcement for themself (the app's « accept » button). Anything else → 403.
     */
    public Mono<AnnouncementSnapshot> requireCanElect(UUID announcementId, UUID responseId, UUID freelancerId,
                                                      TntSecurityContext ctx) {
        return caller(ctx).flatMap(caller -> snapshot(announcementId).flatMap(a -> {
            if (caller.equals(a.clientId()) || DeliveryStatusApplicationService.isStaff(ctx)) {
                return Mono.just(a);
            }
            UUID elected = responseId != null
                    ? a.responses().stream().filter(r -> r.id().equals(responseId))
                            .map(AnnouncementResponseSnapshot::deliveryPersonId).findFirst().orElse(null)
                    : freelancerId;
            boolean fixedPrice = AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE.equalsIgnoreCase(a.pricingMode());
            if (elected == null || !fixedPrice) {
                return Mono.<AnnouncementSnapshot>error(new AccessDeniedException(
                        "Only the client who published this announcement can choose its courier"));
            }
            return courierIdsOf(ctx).flatMap(ids -> ids.contains(elected)
                    ? Mono.just(a)
                    : Mono.<AnnouncementSnapshot>error(new AccessDeniedException(
                            "A courier can only take an announcement for themself")));
        }));
    }

    /** The caller's courier ids (gofp id and core profile id), empty if the caller is not a courier. */
    private Mono<Set<UUID>> courierIdsOf(TntSecurityContext ctx) {
        return caller(ctx).flatMap(caller -> gofpFreelancerRepository.findByCoreUserId(caller)
                .map(AnnouncementAccessGuard::idsOf)
                .defaultIfEmpty(Set.of()));
    }

    private static Set<UUID> idsOf(GofpFreelancer f) {
        Set<UUID> ids = new java.util.LinkedHashSet<>();
        if (f.getCoreFreelancerId() != null) ids.add(f.getCoreFreelancerId());
        if (f.getId() != null) ids.add(f.getId());
        return ids;
    }

    private Mono<AnnouncementSnapshot> snapshot(UUID announcementId) {
        return tenantContextHolder.currentTenantId()
                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                .flatMap(tenantId -> deliveryAnnouncementPort.findById(tenantId, announcementId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Announcement not found: " + announcementId)));
    }

    private static Mono<UUID> caller(TntSecurityContext ctx) {
        return ctx != null && ctx.userId() != null
                ? Mono.just(ctx.userId())
                : Mono.error(new AccessDeniedException("Authentication required"));
    }
}
