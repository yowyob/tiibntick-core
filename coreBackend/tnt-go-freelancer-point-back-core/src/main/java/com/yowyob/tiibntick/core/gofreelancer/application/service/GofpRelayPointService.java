package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.GofpRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpRelayPointRepository;
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
 * <p><strong>Composition:</strong> loads the transient {@code owner} (GofpFreelancer)
 * after each read so callers get the relay point with full owner context.
 * Owner contact fields (phone, email, name) are also denormalised directly on
 * the entity for fast notification lookup without extra joins.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GofpRelayPointService implements GofpRelayPointUseCase {

    private final GofpRelayPointRepository  relayPointRepository;
    private final GofpFreelancerRepository  freelancerRepository;

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
        return relayPointRepository.findByCoreRelayPointId(rp.getCoreRelayPointId())
                .flatMap(existing -> {
                    rp.setId(existing.getId());
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
                .flatMap(this::hydrate);
    }

    @Override
    public Mono<GofpRelayPoint> updateStatus(UUID id, RelayPointStatus status) {
        return relayPointRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                .flatMap(rp -> {
                    rp.setStatus(status);
                    if (status != RelayPointStatus.APPROVED) rp.setIsActive(false);
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
        return relayPointRepository.findAllActiveApproved().flatMap(this::hydrate);
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
        return relayPointRepository.findAllWithSufficientCapacity(requiredVolumeM3)
                .flatMap(this::hydrate);
    }
}
