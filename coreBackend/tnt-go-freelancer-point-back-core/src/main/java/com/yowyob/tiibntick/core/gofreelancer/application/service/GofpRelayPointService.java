package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase;

import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service for GofpRelayPoint lifecycle.
 *
 * <p>{@code coreRelayPointId} maps to {@code tnt-geo-core} {@code RelayHub.id}.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GofpRelayPointService implements GofpRelayPointUseCase {

    private final GofpRelayPointRepository  relayPointRepository;
    private final GofpFreelancerRepository  freelancerRepository;
    /** D-130: bounds of the storage of an independent relay (administration, lot L1). */
    private final ManageDeliveryThresholdsUseCase deliveryThresholds;

    /** The approval machine (D-138, D-139), shared with the admin routes. Optional only for tests. */
    private AdminRelayPointUseCase adminRelayPoints;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAdminRelayPoints(AdminRelayPointUseCase adminRelayPoints) {
        this.adminRelayPoints = adminRelayPoints;
    }

    /** The core hub registry, to leave out relay points that cannot take a parcel (D-144, D-146). Optional for tests. */
    private com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort relayHubs;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRelayHubs(com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort relayHubs) {
        this.relayHubs = relayHubs;
    }

    /**
     * D-144, D-146: a suggested relay point must be a destination the sender can choose — its core hub known,
     * APPROVED, open and not full. Relay points without such a hub are left out of the suggestion lists.
     */
    private Flux<GofpRelayPoint> proposable(Flux<GofpRelayPoint> relayPoints) {
        if (relayHubs == null) return relayPoints;
        return relayPoints.concatMap(rp -> rp.getCoreRelayPointId() == null ? Mono.<GofpRelayPoint>empty()
                : relayHubs.findHub(rp.getCoreRelayPointId(), null)
                        .filter(hub -> hub.isAvailable() && hub.availableSlots() > 0)
                        .map(hub -> rp)
                        .onErrorResume(e -> {
                            log.debug("Relay point {} left out of the suggestions: {}", rp.getId(), e.getMessage());
                            return Mono.empty();
                        }));
    }

    // ── Composition helper ────────────────────────────────────────────────

    private Mono<GofpRelayPoint> hydrate(GofpRelayPoint rp) {
        if (rp.getCoreFreelancerId() == null) return Mono.just(rp);
        return freelancerRepository.findByCoreFreelancerId(rp.getCoreFreelancerId())
                .doOnNext(rp::setOwner)
                .thenReturn(rp)
                .onErrorResume(e -> {
                    log.warn("Could not hydrate owner for relay point {}: {}", rp.getId(), e.getMessage());
                    return Mono.just(rp);
                });
    }

    // ── Write operations ──────────────────────────────────────────────────

    @Override
    public Mono<GofpRelayPoint> createOrUpdate(GofpRelayPoint rp) {
        if (rp.getCoreRelayPointId() == null) {
            return Mono.error(new IllegalArgumentException("coreRelayPointId is required"));
        }
        // D-130: the storage of an independent relay must stay within the platform bounds (400 otherwise)
        Mono<Void> storageCheck = rp.getStorageDays() == null ? Mono.empty()
                : deliveryThresholds.checkIndependentRelayStorageDays(rp.getStorageDays());
        return storageCheck.then(relayPointRepository.findByCoreRelayPointId(rp.getCoreRelayPointId())
                .flatMap(existing -> {
                    rp.setId(existing.getId());
                    rp.markNotNew();
                    rp.setCreatedAt(existing.getCreatedAt());
                    rp.setUpdatedAt(Instant.now());
                    log.info("Updating GofpRelayPoint coreRelayPointId={}", rp.getCoreRelayPointId());
                    return relayPointRepository.save(rp);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    if (rp.getId() == null) rp.setId(UUID.randomUUID());
                    rp.setCreatedAt(Instant.now());
                    rp.setUpdatedAt(Instant.now());
                    log.info("Creating GofpRelayPoint coreRelayPointId={}", rp.getCoreRelayPointId());
                    return relayPointRepository.save(rp);
                }))
                .flatMap(this::hydrate));
    }

    /**
     * Status change by the staff: routed through the approval machine and the core hub (D-138, D-139) — see
     * {@link AdminRelayPointUseCase#changeStatus}; returns the relay point as it is afterwards.
     */
    @Override
    public Mono<GofpRelayPoint> updateStatus(UUID id, RelayPointStatus status) {
        if (adminRelayPoints != null) {
            return relayPointRepository.findById(id)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                    .flatMap(rp -> adminRelayPoints.changeStatus(rp.getId(), status, null, null))
                    .then(Mono.defer(() -> relayPointRepository.findById(id)))
                    .flatMap(this::hydrate);
        }
        return relayPointRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                .flatMap(rp -> {
                    rp.setStatus(status);
                    rp.setIsActive(status == RelayPointStatus.APPROVED);
                    rp.setUpdatedAt(Instant.now());
                    return relayPointRepository.save(rp);
                })
                .flatMap(this::hydrate);
    }

    @Override
    public Mono<GofpRelayPoint> setActive(UUID id, Boolean active) {
        return relayPointRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                .flatMap(rp -> {
                    rp.setIsActive(active);
                    rp.setUpdatedAt(Instant.now());
                    return relayPointRepository.save(rp);
                })
                .flatMap(this::hydrate);
    }

    /**
     * Re-syncs the denormalised owner contact fields from the linked GofpFreelancer/GofpUser.
     * Call this whenever the owner updates their profile.
     */
    @Override
    public Mono<GofpRelayPoint> syncOwnerContact(UUID coreRelayPointId) {
        return relayPointRepository.findByCoreRelayPointId(coreRelayPointId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + coreRelayPointId)))
                .flatMap(rp -> freelancerRepository.findByCoreFreelancerId(rp.getCoreFreelancerId())
                        .flatMap(freelancer -> {
                            if (freelancer.getUser() != null) {
                                rp.setOwnerFirstName(freelancer.getUser().getFirstName());
                                rp.setOwnerLastName(freelancer.getUser().getLastName());
                                rp.setOwnerEmail(freelancer.getUser().getEmail());
                                rp.setOwnerPhone(freelancer.getUser().getPhone());
                            }
                            rp.setUpdatedAt(Instant.now());
                            return relayPointRepository.save(rp);
                        })
                );
    }

    // ── Read operations ───────────────────────────────────────────────────

    @Override
    public Mono<GofpRelayPoint> findById(UUID id) {
        return relayPointRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                .flatMap(this::hydrate);
    }

    @Override
    public Mono<GofpRelayPoint> findByCoreRelayPointId(UUID coreRelayPointId) {
        return relayPointRepository.findByCoreRelayPointId(coreRelayPointId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + coreRelayPointId)))
                .flatMap(this::hydrate);
    }

    @Override
    public Flux<GofpRelayPoint> findByFreelancer(UUID coreFreelancerId) {
        return relayPointRepository.findAllByCoreFreelancerId(coreFreelancerId).flatMap(this::hydrate);
    }

    @Override
    public Flux<GofpRelayPoint> findAll() {
        return relayPointRepository.findAll().flatMap(this::hydrate);
    }

    @Override
    public Flux<GofpRelayPoint> findActiveApproved() {
        return proposable(relayPointRepository.findAllActiveApproved()).flatMap(this::hydrate);
    }

    @Override
    public Flux<GofpRelayPoint> findByStatus(RelayPointStatus status) {
        return relayPointRepository.findAllByStatus(status).flatMap(this::hydrate);
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return relayPointRepository.deleteById(id);
    }

    /**
     * Returns active/approved relay points with enough remaining storage
     * capacity for a packet of {@code requiredVolumeM3} m³.
     *
     * <p>The SQL query handles the unit conversion (m/cm/mm) and subtracts
     * the volume already occupied by active deposits.
     *
     * @param requiredVolumeM3 volume of the packet in m³ (must be > 0)
     */
    @Override
    public Flux<GofpRelayPoint> findWithSufficientCapacity(double requiredVolumeM3) {
        if (requiredVolumeM3 <= 0) {
            log.warn("findWithSufficientCapacity called with non-positive volume {}. Falling back to findActiveApproved.", requiredVolumeM3);
            return findActiveApproved();
        }
        log.info("Searching relay points with sufficient capacity for {} m³", requiredVolumeM3);
        return proposable(relayPointRepository.findAllWithSufficientCapacity(requiredVolumeM3))
                .flatMap(this::hydrate);
    }
}
