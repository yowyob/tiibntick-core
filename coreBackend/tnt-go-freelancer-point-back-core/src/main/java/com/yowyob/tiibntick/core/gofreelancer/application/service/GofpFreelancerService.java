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
     * created when absent, always PENDING (an administrator approves it through {@link #updateStatus}, lot L3), so
     * that the status read back and the vetting transitions find it. An existing one is kept unchanged.
     */
    private Mono<DeliveryPerson> ensureDeliveryPerson(GofpFreelancer f) {
        return tenantContextHolder.currentTenantId().flatMap(tenantId -> deliveryPersonRepository
                .findById(tenantId, f.getId())
                .switchIfEmpty(Mono.defer(() -> {
                    FreelancerStatus initial = FreelancerStatus.PENDING; // no self-approval through the body
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
        return freelancerRepository.findById(id)
                .switchIfEmpty(Mono.error(new FreelancerNotFoundException("GofpFreelancer not found: " + id)))
                .flatMap(f -> {
                    if (f.getStatus() == status) {
                        return Mono.just(f); // idempotent: same state, nothing written
                    }
                    if (!FreelancerStatus.isAllowed(f.getStatus(), status)) {
                        return Mono.error(new InvalidFreelancerStatusTransitionException(f.getStatus(), status));
                    }
                    return tenantContextHolder.currentTenantId()
                            .flatMap(tenantId -> deliveryPersonRepository.findById(tenantId, f.getId()))
                            .switchIfEmpty(Mono.error(new FreelancerNotFoundException(
                                    "No delivery person in the core for freelancer " + id)))
                            .flatMap(dp -> {
                                FreelancerStatus previous = f.getStatus();
                                dp.transitionTo(status.toCore());
                                return deliveryPersonRepository.save(dp).flatMap(saved -> {
                                    f.setStatus(FreelancerStatus.fromCore(saved.getStatus()));
                                    if (status != FreelancerStatus.APPROVED) f.setIsActive(false);
                                    f.setUpdatedAt(Instant.now());
                                    log.info("Freelancer {} status {} -> {} (delivery-core)", id, previous, status);
                                    return freelancerRepository.save(f);
                                });
                            });
                })
                .flatMap(this::hydrate);
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
