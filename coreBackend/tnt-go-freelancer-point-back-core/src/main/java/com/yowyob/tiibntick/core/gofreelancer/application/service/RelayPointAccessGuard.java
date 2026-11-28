package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.time.Instant;
import java.util.UUID;

/**
 * Who may act on a relay point and on the parcels it holds.
 *
 * <p>Ownership link: the caller's token {@code userId} is the {@code coreUserId} of a
 * {@link GofpFreelancer}; a relay point belongs to the freelancer whose
 * {@code coreFreelancerId} it carries ({@code gofp_relay_points.core_freelancer_id} is a
 * foreign key to {@code gofp_freelancers.core_freelancer_id}). The owner is always derived
 * from the caller, never from a request body.
 *
 * <ul>
 *   <li>creating or editing a relay point, its storage and its activity flag: its owner or staff;</li>
 *   <li>approval (status) and deletion: staff only;</li>
 *   <li>a relay point created by its owner starts {@code PENDING} and inactive; status, activity,
 *       rating and counters are never taken from the owner's body;</li>
 *   <li>the deposits of a relay point: its owner or staff; the deposits of a client: that client or staff.</li>
 * </ul>
 * Staff = {@code TNT_ADMIN} or {@code SUPPORT_AGENT} ({@link DeliveryStatusApplicationService#isStaff}).
 */
@Component
@RequiredArgsConstructor
public class RelayPointAccessGuard {

    private final GofpRelayPointRepository relayPoints;
    private final GofpFreelancerRepository freelancers;

    public static boolean isStaff(TntSecurityContext ctx) {
        return DeliveryStatusApplicationService.isStaff(ctx);
    }

    /** Completes for staff, errors with 403 otherwise. */
    public Mono<Void> requireStaff(TntSecurityContext ctx) {
        return caller(ctx).flatMap(c -> isStaff(ctx)
                ? Mono.<Void>empty()
                : Mono.error(new AccessDeniedException("Reserved to platform staff")));
    }

    /** Completes when the caller is the given user, or staff. */
    public Mono<Void> requireSelfOrStaff(UUID userId, TntSecurityContext ctx) {
        return caller(ctx).flatMap(c -> c.equals(userId) || isStaff(ctx)
                ? Mono.<Void>empty()
                : Mono.error(new AccessDeniedException("These deposits belong to another client")));
    }

    /**
     * The relay point (by its id or its {@code coreRelayPointId}) when the caller owns it or is staff.
     * Unknown relay point → {@link IllegalArgumentException} (404 at the edge).
     */
    public Mono<GofpRelayPoint> requireOwnerOrStaff(UUID idOrHubId, TntSecurityContext ctx) {
        return caller(ctx).flatMap(c -> resolve(idOrHubId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Relay point not found: " + idOrHubId)))
                .flatMap(rp -> isStaff(ctx)
                        ? Mono.just(rp)
                        : ownerProfile(ctx).flatMap(owner -> owns(owner, rp)
                                ? Mono.just(rp)
                                : Mono.<GofpRelayPoint>error(new AccessDeniedException(
                                        "This relay point belongs to someone else")))));
    }

    /** The caller's own relay points, most recent first (empty when the caller owns none). */
    public Flux<GofpRelayPoint> myRelayPoints(TntSecurityContext ctx) {
        return caller(ctx).flatMapMany(c -> freelancers.findByCoreUserId(c)
                .flatMapMany(owner -> relayPoints.findAllByCoreFreelancerId(owner.getCoreFreelancerId()))
                .sort(Comparator.comparing(GofpRelayPoint::getCreatedAt,
                        Comparator.nullsLast(Comparator.<Instant>naturalOrder())).reversed()));
    }

    /**
     * Prepares a relay point body for {@code createOrUpdate}. Staff bodies pass unchanged. For anyone
     * else: the owner is the caller; an existing relay point must be theirs and keeps its status,
     * activity, rating and counters; a new one starts {@code PENDING}, inactive, with zero counters.
     */
    public Mono<GofpRelayPoint> prepareWrite(GofpRelayPoint body, TntSecurityContext ctx) {
        return caller(ctx).flatMap(c -> {
            if (isStaff(ctx)) {
                return Mono.just(body);
            }
            return ownerProfile(ctx).flatMap(owner -> {
                Mono<GofpRelayPoint> existing = body.getCoreRelayPointId() == null
                        ? Mono.empty()
                        : relayPoints.findByCoreRelayPointId(body.getCoreRelayPointId());
                return existing
                        .flatMap(rp -> owns(owner, rp)
                                ? Mono.just(keepProtectedFields(body, rp))
                                : Mono.<GofpRelayPoint>error(new AccessDeniedException(
                                        "This relay point belongs to someone else")))
                        .switchIfEmpty(Mono.fromSupplier(() -> asNewPending(body, owner)));
            });
        });
    }

    /**
     * Prepares the caller's own relay point for an upsert: the caller's most recent relay point is
     * updated when there is one, otherwise a new pending one is created. Never staff-elevated:
     * this is the owner's own entry point.
     */
    public Mono<GofpRelayPoint> prepareMine(GofpRelayPoint body, TntSecurityContext ctx) {
        return ownerProfile(ctx).flatMap(owner -> myRelayPoints(ctx).next()
                .map(rp -> {
                    body.setCoreRelayPointId(rp.getCoreRelayPointId());
                    return keepProtectedFields(body, rp);
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    body.setCoreRelayPointId(null);
                    return asNewPending(body, owner);
                })));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static GofpRelayPoint keepProtectedFields(GofpRelayPoint body, GofpRelayPoint existing) {
        body.setId(existing.getId());
        body.setCoreFreelancerId(existing.getCoreFreelancerId());
        body.setStatus(existing.getStatus());
        body.setIsActive(existing.getIsActive());
        body.setRating(existing.getRating());
        body.setTotalDeposits(existing.getTotalDeposits());
        body.setSubscriptionId(existing.getSubscriptionId());
        body.setDepositsUsed(existing.getDepositsUsed());
        body.setMaxDeposits(existing.getMaxDeposits());
        // D-185: the declared capacity is the owner's to change; absent from the body = unchanged
        if (body.getParcelCapacity() == null) body.setParcelCapacity(existing.getParcelCapacity());
        if (body.getName() == null) body.setName(existing.getName());
        if (body.getPostalAddressId() == null) body.setPostalAddressId(existing.getPostalAddressId());
        if (body.getPhysicalAccessAddressId() == null) body.setPhysicalAccessAddressId(existing.getPhysicalAccessAddressId());
        if (body.getStorageLength() == null) body.setStorageLength(existing.getStorageLength());
        if (body.getStorageWidth() == null) body.setStorageWidth(existing.getStorageWidth());
        if (body.getStorageHeight() == null) body.setStorageHeight(existing.getStorageHeight());
        if (body.getStorageDimensionUnit() == null) body.setStorageDimensionUnit(existing.getStorageDimensionUnit());
        if (body.getOwnerFirstName() == null) body.setOwnerFirstName(existing.getOwnerFirstName());
        if (body.getOwnerLastName() == null) body.setOwnerLastName(existing.getOwnerLastName());
        if (body.getOwnerEmail() == null) body.setOwnerEmail(existing.getOwnerEmail());
        if (body.getOwnerPhone() == null) body.setOwnerPhone(existing.getOwnerPhone());
        return body;
    }

    private static GofpRelayPoint asNewPending(GofpRelayPoint body, GofpFreelancer owner) {
        body.setId(null);
        if (body.getCoreRelayPointId() == null) body.setCoreRelayPointId(UUID.randomUUID());
        body.setCoreFreelancerId(owner.getCoreFreelancerId());
        body.setStatus(RelayPointStatus.PENDING);
        body.setIsActive(false);
        body.setRating(null);
        body.setTotalDeposits(0);
        body.setSubscriptionId(null);
        body.setDepositsUsed(0);
        body.setMaxDeposits(null);
        return body;
    }

    private Mono<GofpRelayPoint> resolve(UUID idOrHubId) {
        return relayPoints.findById(idOrHubId)
                .switchIfEmpty(Mono.defer(() -> relayPoints.findByCoreRelayPointId(idOrHubId)));
    }

    private Mono<GofpFreelancer> ownerProfile(TntSecurityContext ctx) {
        return caller(ctx).flatMap(c -> freelancers.findByCoreUserId(c)
                .filter(f -> f.getCoreFreelancerId() != null)
                .switchIfEmpty(Mono.error(new AccessDeniedException(
                        "A relay point owner needs a Go Freelancer Point profile"))));
    }

    private static boolean owns(GofpFreelancer owner, GofpRelayPoint rp) {
        return owner.getCoreFreelancerId() != null && owner.getCoreFreelancerId().equals(rp.getCoreFreelancerId());
    }

    private static Mono<UUID> caller(TntSecurityContext ctx) {
        return ctx != null && ctx.userId() != null
                ? Mono.just(ctx.userId())
                : Mono.error(new AccessDeniedException("Authentication required"));
    }
}
