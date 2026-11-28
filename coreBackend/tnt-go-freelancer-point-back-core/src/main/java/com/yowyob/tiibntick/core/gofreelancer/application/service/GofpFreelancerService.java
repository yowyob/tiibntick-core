package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.exception.FreelancerNotFoundException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.InvalidFreelancerStatusTransitionException;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpFreelancerUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsClass;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service for GofpFreelancer lifecycle.
 *
 * <p><strong>Composition:</strong> the transient {@code user} field is hydrated
 * from {@link GofpUserRepository} after each load so callers get a fully
 * enriched object (identity + professional data in one response).
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GofpFreelancerService implements GofpFreelancerUseCase {

    private final GofpFreelancerRepository freelancerRepository;
    private final GofpUserRepository       userRepository;
    private final DeliveryPersonRepository deliveryPersonRepository;
    private final TenantContextHolder      tenantContextHolder;

    /** Dedicated log of the approvals decided without a person (D-181) or across tenants (D-182). */
    private static final org.slf4j.Logger audit = org.slf4j.LoggerFactory.getLogger("tnt.audit.freelancer-approval");

    /**
     * Decision D-181 (2026-11-28): manual approval of couriers is suspended until the admin platform exists, like the
     * relays (D-158). On (default), a new courier's delivery person is created APPROVED, and the couriers still
     * PENDING are approved once at startup. Off: PENDING until an administrator approves it.
     */
    @org.springframework.beans.factory.annotation.Value("${tnt.gofp.freelancer.auto-approve:true}")
    private boolean autoApprove = true;

    void setAutoApprove(boolean autoApprove) {
        this.autoApprove = autoApprove;
    }

    // ── Composition helper ────────────────────────────────────────────────

    private Mono<GofpFreelancer> hydrate(GofpFreelancer f) {
        if (f.getCoreUserId() == null) return Mono.just(f);
        return userRepository.findByCoreUserId(f.getCoreUserId())
                .doOnNext(f::setUser)
                .thenReturn(f)
                .onErrorResume(e -> {
                    log.warn("Could not hydrate GofpUser for freelancer {}: {}", f.getId(), e.getMessage());
                    return Mono.just(f);
                });
    }

    // ── Write operations ──────────────────────────────────────────────────

    @Override
    public Mono<GofpFreelancer> createOrUpdate(GofpFreelancer freelancer) {
        if (freelancer.getCoreFreelancerId() == null) {
            return Mono.error(new IllegalArgumentException("coreFreelancerId is required"));
        }
        return freelancerRepository.findByCoreFreelancerId(freelancer.getCoreFreelancerId())
                .flatMap(existing -> {
                    freelancer.setId(existing.getId());
                    freelancer.markNotNew();
                    freelancer.setCreatedAt(existing.getCreatedAt());
                    freelancer.setUpdatedAt(Instant.now());
                    log.info("Updating GofpFreelancer coreFreelancerId={}", freelancer.getCoreFreelancerId());
                    return freelancerRepository.save(freelancer);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    if (freelancer.getCoreUserId() == null) {
                        return Mono.error(new IllegalArgumentException("coreUserId is required to create a freelancer"));
                    }
                    freelancer.setId(freelancer.getCoreFreelancerId()); // profile id = delivery person id (lot L3, C1)
                    freelancer.setCreatedAt(Instant.now());
                    freelancer.setUpdatedAt(Instant.now());
                    log.info("Creating GofpFreelancer id=coreFreelancerId={}", freelancer.getCoreFreelancerId());
                    return ensureDeliveryPerson(freelancer).then(freelancerRepository.save(freelancer));
                }))
                .flatMap(this::hydrate);
    }

    /**
     * The delivery-core delivery person carrying the vetting status, at the id of the freelancer (lot L3, C1):
     * created when absent, PENDING (an administrator approves it through {@link #updateStatus}, lot L3) — or APPROVED
     * while the auto-approval of D-181 is on — so that the status read back and the vetting transitions find it. An
     * existing one is kept unchanged. The body of the request never chooses the status.
     */
    private Mono<DeliveryPerson> ensureDeliveryPerson(GofpFreelancer f) {
        return tenantContextHolder.currentTenantId().flatMap(tenantId -> deliveryPersonRepository
                .findById(tenantId, f.getId())
                .switchIfEmpty(Mono.defer(() -> {
                    // never from the body: PENDING, or APPROVED by the platform rule of D-181
                    FreelancerStatus initial = autoApprove ? FreelancerStatus.APPROVED : FreelancerStatus.PENDING;
                    if (autoApprove) {
                        audit.info("Courier {} (actor {}) auto-approved at creation in tenant {} (D-181)",
                                f.getId(), f.getCoreUserId(), tenantId);
                    }
                    DeliveryPerson dp = DeliveryPerson.builder()
                            .id(f.getId())
                            .tenantId(tenantId)
                            .actorId(f.getCoreUserId())
                            .logisticsType(LogisticsType.MOTORBIKE)
                            .logisticsClass(LogisticsClass.STANDARD)
                            .tankCapacity(5.0)
                            .grossFloor(0.0)
                            .totalSeatNumber(2)
                            .remainingDeliveries(f.getRemainingDeliveries() != null ? f.getRemainingDeliveries() : 0)
                            .failedDeliveries(0)
                            .totalDeliveries(0)
                            .status(initial.toCore())
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                    log.info("Creating tnt_delivery_persons id={} actorId={} status={}", dp.getId(), dp.getActorId(),
                            dp.getStatus());
                    return deliveryPersonRepository.save(dp);
                })));
    }

    /**
     * Applies a vetting transition on the delivery-core delivery person (same id as the profile), which owns the
     * status and its state machine (lot L3). The Go row only keeps its operational {@code isActive} flag, switched
     * off when the freelancer leaves APPROVED.
     */
    @Override
    public Mono<GofpFreelancer> updateStatus(UUID id, FreelancerStatus status) {
        return courierTenantForAdmin(id)
                .flatMap(tenant -> TenantContextHolder.underTenant(tenant.orElse(null), applyStatus(id, status)));
    }

    /**
     * Decision D-182: a courier absent from the caller's tenant is looked up everywhere when the caller is a platform
     * administrator; the whole transition then runs in the courier's own tenant (status read and written there). Empty
     * = the caller's tenant, unchanged. The cross-tenant access is written to {@code tnt.audit.freelancer-approval}.
     */
    private Mono<java.util.Optional<UUID>> courierTenantForAdmin(UUID id) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> deliveryPersonRepository.findById(tenantId, id)
                        .map(local -> java.util.Optional.<UUID>empty())
                        .switchIfEmpty(Mono.defer(() -> tenantContextHolder.callerIsPlatformAdmin()
                                .filter(Boolean::booleanValue)
                                .flatMap(admin -> deliveryPersonRepository.findByIdInAnyTenant(id))
                                .doOnNext(dp -> audit.info("TNT_ADMIN (token tenant {}) changes courier {} of tenant {}"
                                        + " (D-182)", tenantId, id, dp.getTenantId()))
                                .map(dp -> java.util.Optional.of(dp.getTenantId())))))
                .defaultIfEmpty(java.util.Optional.empty());
    }

    /**
     * The transition itself, in the current tenant. The delivery person is looked up first: a courier without one here
     * is not found (404) — its status would otherwise read as the PENDING default and give a misleading 409.
     */
    private Mono<GofpFreelancer> applyStatus(UUID id, FreelancerStatus status) {
        return freelancerRepository.findById(id)
                .switchIfEmpty(Mono.error(new FreelancerNotFoundException("GofpFreelancer not found: " + id)))
                .flatMap(f -> tenantContextHolder.currentTenantId()
                        .flatMap(tenantId -> deliveryPersonRepository.findById(tenantId, f.getId()))
                        .switchIfEmpty(Mono.error(new FreelancerNotFoundException(
                                "No delivery person in the core for freelancer " + id)))
                        .flatMap(dp -> {
                            FreelancerStatus previous = FreelancerStatus.fromCore(dp.getStatus());
                            f.setStatus(previous);
                            if (previous == status) {
                                return Mono.just(f); // idempotent: same state, nothing written
                            }
                            if (!FreelancerStatus.isAllowed(previous, status)) {
                                return Mono.error(new InvalidFreelancerStatusTransitionException(previous, status));
                            }
                            dp.transitionTo(status.toCore());
                            return deliveryPersonRepository.save(dp).flatMap(saved -> {
                                f.setStatus(FreelancerStatus.fromCore(saved.getStatus()));
                                if (status != FreelancerStatus.APPROVED) f.setIsActive(false);
                                f.setUpdatedAt(Instant.now());
                                log.info("Freelancer {} status {} -> {} (delivery-core)", id, previous, status);
                                return freelancerRepository.save(f);
                            });
                        }))
                .flatMap(this::hydrate);
    }

    /**
     * D-181: the couriers registered while approval was manual are approved once at startup, through the same state
     * machine. REJECTED, SUSPENDED or other staff decisions are never touched.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void catchUpPendingCouriers() {
        if (!autoApprove) return;
        approvePendingCouriers()
                .subscribe(n -> { if (n > 0) log.info("{} PENDING courier(s) auto-approved at startup (D-181)", n); },
                        e -> log.warn("Courier auto-approval catch-up failed: {}", e.getMessage()));
    }

    /** Approves every PENDING courier (any tenant); returns how many were approved. */
    Mono<Long> approvePendingCouriers() {
        return freelancerRepository.findAllByStatus(FreelancerStatus.PENDING)
                .concatMap(f -> deliveryPersonRepository.findByIdInAnyTenant(f.getId())
                        .filter(dp -> dp.getStatus() == FreelancerStatus.PENDING.toCore())
                        .flatMap(dp -> {
                            dp.transitionTo(FreelancerStatus.APPROVED.toCore());
                            return deliveryPersonRepository.save(dp);
                        })
                        .doOnNext(dp -> audit.info("Courier {} of tenant {} auto-approved (catch-up, D-181)",
                                dp.getId(), dp.getTenantId()))
                        .onErrorResume(e -> {
                            log.warn("Catch-up auto-approval of courier {} failed: {}", f.getId(), e.getMessage());
                            return Mono.empty();
                        }))
                .count();
    }

    @Override
    public Mono<GofpFreelancer> setActive(UUID id, Boolean active) {
        return freelancerRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpFreelancer not found: " + id)))
                .flatMap(f -> {
                    f.setIsActive(active);
                    f.setUpdatedAt(Instant.now());
                    return freelancerRepository.save(f);
                })
                .flatMap(this::hydrate);
    }

    @Override
    public Mono<GofpFreelancer> updateLocation(UUID coreFreelancerId, Float lat, Float lng) {
        return freelancerRepository.findByCoreFreelancerId(coreFreelancerId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpFreelancer not found for coreFreelancerId: " + coreFreelancerId)))
                .flatMap(f -> {
                    f.setLatitudeGps(lat);
                    f.setLongitudeGps(lng);
                    f.setUpdatedAt(Instant.now());
                    return freelancerRepository.save(f);
                });
        // No hydration needed for location updates — performance critical
    }

    @Override
    public Mono<GofpFreelancer> recordFailedDelivery(UUID coreFreelancerId) {
        return freelancerRepository.findByCoreFreelancerId(coreFreelancerId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpFreelancer not found: " + coreFreelancerId)))
                .flatMap(f -> {
                    f.setFailedDeliveries((f.getFailedDeliveries() == null ? 0 : f.getFailedDeliveries()) + 1);
                    f.setUpdatedAt(Instant.now());
                    return freelancerRepository.save(f);
                });
    }

    @Override
    public Mono<GofpFreelancer> recordSuccessfulDelivery(UUID coreFreelancerId) {
        return freelancerRepository.findByCoreFreelancerId(coreFreelancerId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpFreelancer not found: " + coreFreelancerId)))
                .flatMap(f -> {
                    f.setTotalDeliveries((f.getTotalDeliveries() == null ? 0 : f.getTotalDeliveries()) + 1);
                    int remaining = (f.getRemainingDeliveries() == null ? 0 : f.getRemainingDeliveries());
                    if (remaining > 0) f.setRemainingDeliveries(remaining - 1);
                    f.setUpdatedAt(Instant.now());
                    return freelancerRepository.save(f);
                });
    }

    // ── Read operations ───────────────────────────────────────────────────

    @Override
    public Mono<GofpFreelancer> findById(UUID id) {
        return freelancerRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpFreelancer not found: " + id)))
                .flatMap(this::hydrate);
    }

    @Override
    public Mono<GofpFreelancer> findByCoreFreelancerId(UUID coreFreelancerId) {
        return freelancerRepository.findByCoreFreelancerId(coreFreelancerId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpFreelancer not found for coreFreelancerId: " + coreFreelancerId)))
                .flatMap(this::hydrate);
    }

    @Override
    public Mono<GofpFreelancer> findByCoreUserId(UUID coreUserId) {
        return freelancerRepository.findByCoreUserId(coreUserId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpFreelancer not found for coreUserId: " + coreUserId)))
                .flatMap(this::hydrate);
    }

    @Override
    public Flux<GofpFreelancer> findAll() {
        return freelancerRepository.findAll().flatMap(this::hydrate);
    }

    @Override
    public Flux<GofpFreelancer> findByStatus(FreelancerStatus status) {
        return freelancerRepository.findAllByStatus(status).flatMap(this::hydrate);
    }

    @Override
    public Flux<GofpFreelancer> findActiveApproved() {
        return freelancerRepository.findAllByStatusAndIsActive(FreelancerStatus.APPROVED, true)
                .flatMap(this::hydrate);
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return freelancerRepository.deleteById(id);
    }
}
