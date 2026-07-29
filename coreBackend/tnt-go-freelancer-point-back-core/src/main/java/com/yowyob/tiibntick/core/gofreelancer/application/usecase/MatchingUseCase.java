package com.yowyob.tiibntick.core.gofreelancer.application.usecase;

import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerVehicleApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IGeolocationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase.FreelancerCandidate;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerVehicle;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpFreelancerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Matching service — Layer 6 (tnt-go-freelancer-point-back-core).
 *
 * Responsibilities:
 *   1. Consumes "announcement-published" Kafka events.
 *   2. Finds eligible freelancer candidates via spatial Haversine filter.
 *   3. Pre-filters by available trunk volume (hard constraint).
 *   4. Ranks survivors with TOPSIS (C3 = availableTrunkVolumeM3).
 *   5. Publishes the ranked list to "announcement-candidates-ranked".
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MatchingUseCase {

    private final TopsisRankingUseCase topsisRankingUseCase;
    private final DeliveryQueryUseCase deliveryQueryUseCase;
    private final IGeolocationPort geolocationPort;
    private final FreelancerVehicleRepository freelancerVehicleRepository;
    private final DeliveryRepository deliveryRepository;
    private final FreelancerVehicleApplicationService vehicleApplicationService;
    private final GofpFreelancerRepository gofpFreelancerRepository;

    // Outgoing ports — uncomment when adapters are ready
    // private final IFreelancerProviderPort freelancerProviderPort;
    // private final IEventPublisherPort eventPublisherPort;

    /** Max search radius before giving up (km). */
    private static final double INITIAL_DELTA_KM   = 1.5;
    private static final double DELTA_INCREMENT_KM  = 0.5;
    private static final double MAX_DELTA_KM        = 10.0;

    // ── Kafka consumer ────────────────────────────────────────────────────────

    /**
     * Entry point triggered when an announcement is published.
     * The event payload carries the announcement UUID as a plain string.
     */
    @KafkaListener(topics = "announcement-published", groupId = "tnt-go-freelancer-core-group")
    public void onAnnouncementPublished(String announcementId) {
        log.info("[Matching] Received announcement-published event: {}", announcementId);
        processMatchingForAnnouncement(UUID.fromString(announcementId))
                .doOnError(e -> log.error("[Matching] Failed for announcement {}: {}", announcementId, e.getMessage()))
                .subscribe();
    }

    // ── Matching pipeline ─────────────────────────────────────────────────────

    public Mono<Void> processMatchingForAnnouncement(UUID announcementId) {
        UUID tenantId = TenantContextHolder.systemTenant();
        return deliveryQueryUseCase.findAnnouncementById(tenantId, announcementId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Core Announcement not found: " + announcementId)))
                .flatMap(announcement -> {
                    if (announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
                        log.warn("[Matching] Announcement {} is not PUBLISHED (status={}), skipping",
                                announcementId, announcement.getStatus());
                        return Mono.empty();
                    }
                    return runMatching(announcement, INITIAL_DELTA_KM);
                });
    }

    /**
     * Recursive spatial expansion: starts at INITIAL_DELTA_KM, grows by
     * DELTA_INCREMENT_KM until MAX_DELTA_KM if no candidates are found.
     */
    private Mono<Void> runMatching(DeliveryAnnouncement announcement, double delta) {

        Mono<double[]> pickupMono = announcement.getPickupAddress().coordinates() != null
                ? Mono.just(new double[]{announcement.getPickupAddress().coordinates().latitude(), announcement.getPickupAddress().coordinates().longitude()})
                : geolocationPort.getCoordinatesFromAddress(announcement.getPickupAddress().toDisplayString());

        Mono<double[]> deliveryMono = announcement.getDeliveryAddress().coordinates() != null
                ? Mono.just(new double[]{announcement.getDeliveryAddress().coordinates().latitude(), announcement.getDeliveryAddress().coordinates().longitude()})
                : geolocationPort.getCoordinatesFromAddress(announcement.getDeliveryAddress().toDisplayString());

        // Compute packet volume from announcement (dimensions in m → result in m³)
        double packetVolumeM3 = computePacketVolumeM3(announcement);

        return Mono.zip(pickupMono, deliveryMono).flatMap(coords -> {
            double pickupLat  = coords.getT1()[0];
            double pickupLon  = coords.getT1()[1];
            double deliveryLat = coords.getT2()[0];
            double deliveryLon = coords.getT2()[1];

            double distF1F2 = TopsisRankingUseCase.haversine(pickupLat, pickupLon, deliveryLat, deliveryLon);
            double dMax     = distF1F2 + 2 * delta;

            log.info("[Matching] Announcement {} — delta={}km dMax={}km packetVolume={}m³",
                    announcement.getId(), delta, dMax, packetVolumeM3);

            // TODO: replace stub with freelancerProviderPort.findActiveCandidatesNear(...)
            List<FreelancerCandidate> allCandidates = List.of(); // stub

            // ── Step 1: Spatial ellipse filter ────────────────────────────────
            List<FreelancerCandidate> spatialCandidates = allCandidates.stream()
                    .filter(c -> {
                        double d1 = TopsisRankingUseCase.haversine(c.getLatitude(), c.getLongitude(), pickupLat, pickupLon);
                        double d2 = TopsisRankingUseCase.haversine(c.getLatitude(), c.getLongitude(), deliveryLat, deliveryLon);
                        return (d1 + d2) <= dMax;
                    })
                    .toList();

            if (spatialCandidates.isEmpty()) {
                if (delta < MAX_DELTA_KM) {
                    log.info("[Matching] No spatial candidates at delta={}km — expanding search", delta);
                    return runMatching(announcement, delta + DELTA_INCREMENT_KM);
                }
                log.warn("[Matching] No candidates found up to {}km for announcement {}", MAX_DELTA_KM, announcement.getId());
                return Mono.empty();
            }

            // ── Step 2: Enrich candidates with availableTrunkVolumeM3 ─────────
            // then hard-filter those without enough space,
            // then TOPSIS-rank the survivors.
            return enrichWithTrunkVolume(spatialCandidates)
                    .flatMap(enriched -> {
                        // Hard constraint: trunk must fit the packet
                        List<FreelancerCandidate> volumeEligible = packetVolumeM3 <= 0
                                ? enriched   // no packet dimensions → skip volume filter
                                : enriched.stream()
                                        .filter(c -> {
                                            double available = c.getAvailableTrunkVolumeM3() != null
                                                    ? c.getAvailableTrunkVolumeM3() : 0.0;
                                            boolean fits = available >= packetVolumeM3;
                                            if (!fits) {
                                                log.debug("[Matching] Freelancer {} eliminated — trunk {}m³ < packet {}m³",
                                                        c.getFreelancerId(), available, packetVolumeM3);
                                            }
                                            return fits;
                                        })
                                        .toList();

                        if (volumeEligible.isEmpty()) {
                            log.info("[Matching] All {} spatial candidates eliminated by trunk-volume filter for announcement {}",
                                    enriched.size(), announcement.getId());
                            if (delta < MAX_DELTA_KM) {
                                return runMatching(announcement, delta + DELTA_INCREMENT_KM);
                            }
                            log.warn("[Matching] No candidates with sufficient trunk volume up to {}km", MAX_DELTA_KM);
                            return Mono.empty();
                        }

                        log.info("[Matching] {} / {} candidates pass volume filter for announcement {}",
                                volumeEligible.size(), enriched.size(), announcement.getId());

                        // ── Step 3: TOPSIS ranking ────────────────────────────
                        return topsisRankingUseCase.rankCandidates(volumeEligible, pickupLat, pickupLon)
                                .flatMap(ranked -> {
                                    log.info("[Matching] {} ranked candidates for announcement {}", ranked.size(), announcement.getId());
                                    // TODO: eventPublisherPort.publish("announcement-candidates-ranked", ranked)
                                    return Mono.<Void>empty();
                                });
                    });
        });
    }

    /**
     * Entry point for DeliveryNeed matching (synchronous call).
     * Used by DeliveryNeedApplicationService.
     */
    public Mono<List<FreelancerCandidate>> processMatchingForDeliveryNeed(
            UUID deliveryNeedId,
            double pickupLat, double pickupLon,
            double deliveryLat, double deliveryLon,
            double packetVolumeM3,
            java.time.LocalDateTime pickupDeadline) {
            
        return runMatchingForNeed(deliveryNeedId, pickupLat, pickupLon, deliveryLat, deliveryLon, packetVolumeM3, pickupDeadline, INITIAL_DELTA_KM);
    }

    private Mono<List<FreelancerCandidate>> runMatchingForNeed(
            UUID deliveryNeedId,
            double pickupLat, double pickupLon,
            double deliveryLat, double deliveryLon,
            double packetVolumeM3,
            java.time.LocalDateTime pickupDeadline,
            double delta) {

        double distF1F2 = TopsisRankingUseCase.haversine(pickupLat, pickupLon, deliveryLat, deliveryLon);
        double dMax     = distF1F2 + 2 * delta;

        log.info("[Matching] DeliveryNeed {} — delta={}km dMax={}km packetVolume={}m³",
                deliveryNeedId, delta, dMax, packetVolumeM3);

        // TODO: replace stub with freelancerProviderPort.findActiveCandidatesNear(...)
        List<FreelancerCandidate> allCandidates = List.of(); // stub

        // ── Step 1: Spatial ellipse filter ────────────────────────────────
        List<FreelancerCandidate> spatialCandidates = allCandidates.stream()
                .filter(c -> {
                    double d1 = TopsisRankingUseCase.haversine(c.getLatitude(), c.getLongitude(), pickupLat, pickupLon);
                    double d2 = TopsisRankingUseCase.haversine(c.getLatitude(), c.getLongitude(), deliveryLat, deliveryLon);
                    return (d1 + d2) <= dMax;
                })
                .toList();

        if (spatialCandidates.isEmpty()) {
            if (delta < MAX_DELTA_KM) {
                log.info("[Matching] No spatial candidates at delta={}km — expanding search", delta);
                return runMatchingForNeed(deliveryNeedId, pickupLat, pickupLon, deliveryLat, deliveryLon, packetVolumeM3, pickupDeadline, delta + DELTA_INCREMENT_KM);
            }
            log.warn("[Matching] No candidates found up to {}km for deliveryNeed {}", MAX_DELTA_KM, deliveryNeedId);
            return Mono.just(List.of());
        }

        // ── Step 2: Enrich candidates and filter ─────────
        return enrichWithTrunkVolume(spatialCandidates)
                .flatMap(enriched -> {
                    List<FreelancerCandidate> eligible = enriched.stream()
                            .filter(c -> {
                                // 1. Trunk volume (m³) constraint
                                double available = c.getAvailableTrunkVolumeM3() != null
                                        ? c.getAvailableTrunkVolumeM3() : 0.0;
                                boolean fitsVolume = packetVolumeM3 <= 0 || available >= packetVolumeM3;
                                if (!fitsVolume) {
                                    log.debug("[Matching] Freelancer {} eliminated — trunk {}m³ < packet {}m³",
                                            c.getFreelancerId(), available, packetVolumeM3);
                                    return false;
                                }

                                // 2. Capacity Quota constraint
                                boolean hasQuota = c.getRemainingDeliveries() != null && c.getRemainingDeliveries() > 0;
                                if (!hasQuota) {
                                    log.debug("[Matching] Freelancer {} eliminated — no remaining delivery capacity", c.getFreelancerId());
                                    return false;
                                }
                                
                                // 3. Availability constraint (Date/Time)
                                // TODO: Verify against delivery_person_availability using pickupDeadline
                                boolean isAvailable = true; // Placeholder
                                if (!isAvailable) {
                                    log.debug("[Matching] Freelancer {} eliminated — not available at {}", c.getFreelancerId(), pickupDeadline);
                                    return false;
                                }

                                return true;
                            })
                            .toList();

                    if (eligible.isEmpty()) {
                        log.info("[Matching] All {} candidates eliminated by filters for deliveryNeed {}",
                                enriched.size(), deliveryNeedId);
                        if (delta < MAX_DELTA_KM) {
                            return runMatchingForNeed(deliveryNeedId, pickupLat, pickupLon, deliveryLat, deliveryLon, packetVolumeM3, pickupDeadline, delta + DELTA_INCREMENT_KM);
                        }
                        return Mono.just(List.of());
                    }

                    // ── Step 3: Tri par proximité au point de ramassage (premier arrivé) ─────────
                    List<FreelancerCandidate> sortedEligible = eligible.stream()
                            .sorted(java.util.Comparator.comparingDouble(c ->
                                    TopsisRankingUseCase.haversine(c.getLatitude(), c.getLongitude(), pickupLat, pickupLon)
                            ))
                            .toList();

                    return Mono.just(sortedEligible);
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Enriches each candidate with:
     * <ul>
     *   <li>{@code availableTrunkVolumeM3} = trunkTotal - occupiedByActiveDeliveries</li>
     *   <li>{@code rating} from {@link com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer#getRating()} (real value, replaces hardcoded 0.0)</li>
     *   <li>{@code vehicleType} from {@link com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerVehicle} — used for TOPSIS C4 and optional pre-filter</li>
     * </ul>
     */
    private Mono<List<FreelancerCandidate>> enrichWithTrunkVolume(List<FreelancerCandidate> candidates) {
        return reactor.core.publisher.Flux.fromIterable(candidates)
                .flatMap(candidate -> {
                    UUID fId = candidate.getFreelancerId();

                    // ── Trunk volume ──────────────────────────────────────────
                    Mono<FreelancerVehicle> vehicleMono = freelancerVehicleRepository
                            .findByFreelancerId(fId)
                            .defaultIfEmpty(new FreelancerVehicle());

                    Mono<Double> occupiedM3 = deliveryRepository
                            .sumOccupiedVolumeM3ByFreelancerId(fId)
                            .defaultIfEmpty(0.0);

                    // ── Real rating from GofpFreelancer ───────────────────────
                    Mono<com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer> freelancerMono =
                            gofpFreelancerRepository.findById(fId)
                                    .switchIfEmpty(Mono.defer(() -> gofpFreelancerRepository.findByCoreFreelancerId(fId)));

                    return Mono.zip(vehicleMono, occupiedM3, freelancerMono.onErrorResume(e -> Mono.empty()))
                            .map(t -> {
                                FreelancerVehicle vehicle   = t.getT1();
                                double occupied              = t.getT2();
                                com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer gofp = t.getT3();

                                // Available trunk volume
                                double trunkTotal = vehicleApplicationService.calculateVolumeInM3(
                                        vehicle.getTrunkLength(), vehicle.getTrunkWidth(),
                                        vehicle.getTrunkHeight(), vehicle.getTrunkDimensionUnit());
                                candidate.setAvailableTrunkVolumeM3(Math.max(0.0, trunkTotal - occupied));

                                // Real rating (GofpFreelancer.rating) — falls back to 0.0 if absent
                                if (gofp != null && gofp.getRating() != null) {
                                    candidate.setRating(gofp.getRating());
                                }
                                
                                if (gofp != null) {
                                    candidate.setRemainingDeliveries(gofp.getRemainingDeliveries());
                                }

                                // Vehicle type string for TOPSIS C4 and optional filter
                                // FreelancerVehicle doesn't store vehicleType — it's in the core resource.
                                // We resolve it from the R2DBC vehicle row if present (coreVehicleId lookup is async;
                                // for now we leave vehicleType as-is, populated upstream by the candidate provider).

                                return candidate;
                            })
                            // Fallback: if freelancer lookup fails, still enrich volume
                            .switchIfEmpty(Mono.defer(() ->
                                    Mono.zip(vehicleMono, occupiedM3).map(t -> {
                                        FreelancerVehicle vehicle = t.getT1();
                                        double occupied = t.getT2();
                                        double trunkTotal = vehicleApplicationService.calculateVolumeInM3(
                                                vehicle.getTrunkLength(), vehicle.getTrunkWidth(),
                                                vehicle.getTrunkHeight(), vehicle.getTrunkDimensionUnit());
                                        candidate.setAvailableTrunkVolumeM3(Math.max(0.0, trunkTotal - occupied));
                                        return candidate;
                                    })
                            ))
                            .onErrorResume(e -> {
                                log.warn("[Matching] Could not enrich candidate {}: {}", fId, e.getMessage());
                                candidate.setAvailableTrunkVolumeM3(0.0);
                                return Mono.just(candidate);
                            });
                })
                .collectList();
    }

    /**
     * Computes the packet volume in m³ from the announcement's packet dimensions.
     * Dimensions are expected to be in metres. Returns 0.0 if dimensions are absent.
     */
    private double computePacketVolumeM3(DeliveryAnnouncement announcement) {
        if (announcement.getParcel() == null || announcement.getParcel().getSpecification() == null) {
            return 0.0;
        }
        return announcement.getParcel().getSpecification().volumetricWeightDm3() / 1000.0;
    }
}
