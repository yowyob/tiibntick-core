package com.yowyob.tiibntick.core.gofreelancer.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.search.document.AnnouncementDocument;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.search.document.FreelancerDocument;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IEventPublisherPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IFreelancerProviderPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IGeolocationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerVehicleApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NotificationService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase.FreelancerCandidate;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerVehicle;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Matching service — Layer 6 (tnt-go-freelancer-point-back-core).
 *
 * <p>Consumes delivery-core announcement published events, ranks freelancers with TOPSIS,
 * notifies ranked candidates, and publishes the ranked list.
 *
 * <p>Depends only on {@link IDeliveryAnnouncementPort} and its
 * {@link AnnouncementSnapshot} read model — never on {@code tnt-delivery-core}'s own
 * {@code DeliveryAnnouncement} aggregate directly, see {@code architecture/decisions.md}
 * ADR-021.
 *
 * @author MANFOUO BRAUN
 */
@Service
@Slf4j
public class MatchingUseCase {

    private final TopsisRankingUseCase topsisRankingUseCase;
    private final IDeliveryAnnouncementPort deliveryAnnouncementPort;
    private final IGeolocationPort geolocationPort;
    private final FreelancerVehicleRepository freelancerVehicleRepository;
    private final DeliveryRepository deliveryRepository;
    private final FreelancerVehicleApplicationService vehicleApplicationService;
    private final GofpFreelancerRepository gofpFreelancerRepository;
    private final IFreelancerProviderPort freelancerProviderPort;
    private final IEventPublisherPort eventPublisherPort;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    private static final double INITIAL_DELTA_KM = 1.5;
    private static final double DELTA_INCREMENT_KM = 0.5;
    private static final double MAX_DELTA_KM = 10.0;

    public MatchingUseCase(
            TopsisRankingUseCase topsisRankingUseCase,
            IDeliveryAnnouncementPort deliveryAnnouncementPort,
            IGeolocationPort geolocationPort,
            FreelancerVehicleRepository freelancerVehicleRepository,
            DeliveryRepository deliveryRepository,
            FreelancerVehicleApplicationService vehicleApplicationService,
            GofpFreelancerRepository gofpFreelancerRepository,
            IFreelancerProviderPort freelancerProviderPort,
            IEventPublisherPort eventPublisherPort,
            NotificationService notificationService,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.topsisRankingUseCase = topsisRankingUseCase;
        this.deliveryAnnouncementPort = deliveryAnnouncementPort;
        this.geolocationPort = geolocationPort;
        this.freelancerVehicleRepository = freelancerVehicleRepository;
        this.deliveryRepository = deliveryRepository;
        this.vehicleApplicationService = vehicleApplicationService;
        this.gofpFreelancerRepository = gofpFreelancerRepository;
        this.freelancerProviderPort = freelancerProviderPort;
        this.eventPublisherPort = eventPublisherPort;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                    TntTopics.DELIVERY_ANNOUNCEMENT_PUBLISHED,
                    TntTopics.GOFP_ANNOUNCEMENT_PUBLISHED
            },
            groupId = "tnt-go-freelancer-core-group")
    public void onAnnouncementPublished(String payload) {
        UUID announcementId = extractAnnouncementId(payload);
        if (announcementId == null) {
            log.warn("[Matching] Could not extract announcementId from payload: {}", payload);
            return;
        }
        log.info("[Matching] Received announcement-published event: {}", announcementId);
        processMatchingForAnnouncement(announcementId)
                .doOnError(e -> log.error("[Matching] Failed for announcement {}: {}",
                        announcementId, e.getMessage()))
                .subscribe();
    }

    public Mono<Void> processMatchingForAnnouncement(UUID announcementId) {
        UUID tenantId = TenantContextHolder.systemTenant();
        return deliveryAnnouncementPort.findById(tenantId, announcementId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Core Announcement not found: " + announcementId)))
                .flatMap(announcement -> {
                    if (announcement.status() != AnnouncementStatus.PUBLISHED) {
                        log.warn("[Matching] Announcement {} is not PUBLISHED (status={}), skipping",
                                announcementId, announcement.status());
                        return Mono.empty();
                    }
                    return runMatching(announcement, INITIAL_DELTA_KM);
                });
    }

    private Mono<Void> runMatching(AnnouncementSnapshot announcement, double delta) {
        Mono<double[]> pickupMono = announcement.pickupAddress().getCoordinates().isPresent()
                ? Mono.just(new double[]{
                announcement.pickupAddress().getCoordinates().get().getLatitude(),
                announcement.pickupAddress().getCoordinates().get().getLongitude()})
                : geolocationPort.getCoordinatesFromAddress(
                announcement.pickupAddress().toDisplayString());

        Mono<double[]> deliveryMono = announcement.deliveryAddress().getCoordinates().isPresent()
                ? Mono.just(new double[]{
                announcement.deliveryAddress().getCoordinates().get().getLatitude(),
                announcement.deliveryAddress().getCoordinates().get().getLongitude()})
                : geolocationPort.getCoordinatesFromAddress(
                announcement.deliveryAddress().toDisplayString());

        double packetVolumeM3 = announcement.packetVolumetricWeightDm3() / 1000.0;

        return Mono.zip(pickupMono, deliveryMono).flatMap(coords -> {
            double pickupLat = coords.getT1()[0];
            double pickupLon = coords.getT1()[1];
            double deliveryLat = coords.getT2()[0];
            double deliveryLon = coords.getT2()[1];

            double distF1F2 = TopsisRankingUseCase.haversine(
                    pickupLat, pickupLon, deliveryLat, deliveryLon);
            double dMax = distF1F2 + 2 * delta;

            log.info("[Matching] Announcement {} — delta={}km dMax={}km packetVolume={}m³",
                    announcement.id(), delta, dMax, packetVolumeM3);

            return freelancerProviderPort
                    .findActiveCandidatesNear(pickupLat, pickupLon, Math.max(dMax, delta * 2))
                    .flatMap(allCandidates -> {
                        List<FreelancerCandidate> spatialCandidates = allCandidates.stream()
                                .filter(c -> {
                                    double d1 = TopsisRankingUseCase.haversine(
                                            c.getLatitude(), c.getLongitude(), pickupLat, pickupLon);
                                    double d2 = TopsisRankingUseCase.haversine(
                                            c.getLatitude(), c.getLongitude(), deliveryLat, deliveryLon);
                                    return (d1 + d2) <= dMax;
                                })
                                .toList();

                        if (spatialCandidates.isEmpty()) {
                            if (delta < MAX_DELTA_KM) {
                                log.info("[Matching] No spatial candidates at delta={}km — expanding", delta);
                                return runMatching(announcement, delta + DELTA_INCREMENT_KM);
                            }
                            log.warn("[Matching] No candidates found up to {}km for announcement {}",
                                    MAX_DELTA_KM, announcement.id());
                            return Mono.empty();
                        }

                        return enrichWithTrunkVolume(spatialCandidates)
                                .flatMap(enriched -> {
                                    List<FreelancerCandidate> volumeEligible = packetVolumeM3 <= 0
                                            ? enriched
                                            : enriched.stream()
                                            .filter(c -> {
                                                double available = c.getAvailableTrunkVolumeM3() != null
                                                        ? c.getAvailableTrunkVolumeM3() : 0.0;
                                                return available >= packetVolumeM3;
                                            })
                                            .toList();

                                    if (volumeEligible.isEmpty()) {
                                        if (delta < MAX_DELTA_KM) {
                                            return runMatching(announcement, delta + DELTA_INCREMENT_KM);
                                        }
                                        return Mono.empty();
                                    }

                                    return topsisRankingUseCase
                                            .rankCandidates(volumeEligible, pickupLat, pickupLon)
                                            .flatMap(ranked -> publishAndNotify(announcement, ranked));
                                });
                    });
        });
    }

    private Mono<Void> publishAndNotify(
            AnnouncementSnapshot announcement, List<FreelancerCandidate> ranked) {
        if (ranked.isEmpty()) {
            return Mono.empty();
        }
        log.info("[Matching] {} ranked candidates for announcement {}",
                ranked.size(), announcement.id());

        Map<String, Object> rankedPayload = Map.of(
                "announcementId", announcement.id().toString(),
                "tenantId", announcement.tenantId().toString(),
                "candidates", ranked.stream()
                        .map(c -> Map.of(
                                "freelancerId", c.getFreelancerId().toString(),
                                "topsisScore", c.getTopsisScore() != null ? c.getTopsisScore() : 0.0,
                                "latitude", c.getLatitude(),
                                "longitude", c.getLongitude()))
                        .toList());

        Mono<Void> publishRanked = eventPublisherPort
                .publish(TntTopics.GOFP_ANNOUNCEMENT_CANDIDATES_RANKED, rankedPayload)
                .onErrorResume(e -> {
                    log.warn("[Matching] Failed to publish ranked candidates: {}", e.getMessage());
                    return Mono.empty();
                });

        AnnouncementDocument annDoc = AnnouncementDocument.builder()
                .id(announcement.id())
                .build();
        List<FreelancerDocument> freelancers = ranked.stream()
                .map(c -> FreelancerDocument.builder()
                        .id(c.getFreelancerId())
                        .personId(c.getFreelancerId())
                        .location(new GeoPoint(c.getLatitude(), c.getLongitude()))
                        .isActive(true)
                        .isAvailable(true)
                        .vehicleType(c.getVehicleType())
                        .build())
                .toList();

        Mono<Void> notify = notificationService
                .notifyEligibleFreelancers(freelancers, annDoc)
                .then()
                .onErrorResume(e -> {
                    log.warn("[Matching] Notification failed: {}", e.getMessage());
                    return Mono.empty();
                });

        return Mono.when(publishRanked, notify);
    }

    public Mono<List<FreelancerCandidate>> processMatchingForDeliveryNeed(
            UUID deliveryNeedId,
            double pickupLat, double pickupLon,
            double deliveryLat, double deliveryLon,
            double packetVolumeM3,
            java.time.LocalDateTime pickupDeadline) {
        return runMatchingForNeed(
                deliveryNeedId, pickupLat, pickupLon, deliveryLat, deliveryLon,
                packetVolumeM3, pickupDeadline, INITIAL_DELTA_KM);
    }

    private Mono<List<FreelancerCandidate>> runMatchingForNeed(
            UUID deliveryNeedId,
            double pickupLat, double pickupLon,
            double deliveryLat, double deliveryLon,
            double packetVolumeM3,
            java.time.LocalDateTime pickupDeadline,
            double delta) {

        double distF1F2 = TopsisRankingUseCase.haversine(pickupLat, pickupLon, deliveryLat, deliveryLon);
        double dMax = distF1F2 + 2 * delta;

        return freelancerProviderPort
                .findActiveCandidatesNear(pickupLat, pickupLon, Math.max(dMax, delta * 2))
                .flatMap(allCandidates -> {
                    List<FreelancerCandidate> spatialCandidates = allCandidates.stream()
                            .filter(c -> {
                                double d1 = TopsisRankingUseCase.haversine(
                                        c.getLatitude(), c.getLongitude(), pickupLat, pickupLon);
                                double d2 = TopsisRankingUseCase.haversine(
                                        c.getLatitude(), c.getLongitude(), deliveryLat, deliveryLon);
                                return (d1 + d2) <= dMax;
                            })
                            .toList();

                    if (spatialCandidates.isEmpty()) {
                        if (delta < MAX_DELTA_KM) {
                            return runMatchingForNeed(
                                    deliveryNeedId, pickupLat, pickupLon, deliveryLat, deliveryLon,
                                    packetVolumeM3, pickupDeadline, delta + DELTA_INCREMENT_KM);
                        }
                        return Mono.just(List.of());
                    }

                    return enrichWithTrunkVolume(spatialCandidates)
                            .flatMap(enriched -> {
                                List<FreelancerCandidate> eligible = enriched.stream()
                                        .filter(c -> {
                                            double available = c.getAvailableTrunkVolumeM3() != null
                                                    ? c.getAvailableTrunkVolumeM3() : 0.0;
                                            boolean fitsVolume = packetVolumeM3 <= 0 || available >= packetVolumeM3;
                                            boolean hasQuota = c.getRemainingDeliveries() != null
                                                    && c.getRemainingDeliveries() > 0;
                                            return fitsVolume && hasQuota;
                                        })
                                        .toList();

                                if (eligible.isEmpty()) {
                                    if (delta < MAX_DELTA_KM) {
                                        return runMatchingForNeed(
                                                deliveryNeedId, pickupLat, pickupLon, deliveryLat, deliveryLon,
                                                packetVolumeM3, pickupDeadline, delta + DELTA_INCREMENT_KM);
                                    }
                                    return Mono.just(List.of());
                                }

                                return topsisRankingUseCase.rankCandidates(eligible, pickupLat, pickupLon);
                            });
                });
    }

    private Mono<List<FreelancerCandidate>> enrichWithTrunkVolume(List<FreelancerCandidate> candidates) {
        return reactor.core.publisher.Flux.fromIterable(candidates)
                .flatMap(candidate -> {
                    UUID fId = candidate.getFreelancerId();

                    Mono<FreelancerVehicle> vehicleMono = freelancerVehicleRepository
                            .findByFreelancerId(fId)
                            .defaultIfEmpty(new FreelancerVehicle());

                    Mono<Double> occupiedM3 = deliveryRepository
                            .sumOccupiedVolumeM3ByFreelancerId(fId)
                            .defaultIfEmpty(0.0);

                    Mono<com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer> freelancerMono =
                            gofpFreelancerRepository.findById(fId)
                                    .switchIfEmpty(Mono.defer(
                                            () -> gofpFreelancerRepository.findByCoreFreelancerId(fId)));

                    return Mono.zip(vehicleMono, occupiedM3, freelancerMono.onErrorResume(e -> Mono.empty()))
                            .map(t -> {
                                FreelancerVehicle vehicle = t.getT1();
                                double occupied = t.getT2();
                                var gofp = t.getT3();

                                double trunkTotal = vehicleApplicationService.calculateVolumeInM3(
                                        vehicle.getTrunkLength(), vehicle.getTrunkWidth(),
                                        vehicle.getTrunkHeight(), vehicle.getTrunkDimensionUnit());
                                candidate.setAvailableTrunkVolumeM3(Math.max(0.0, trunkTotal - occupied));

                                if (gofp != null && gofp.getRating() != null) {
                                    candidate.setRating(gofp.getRating());
                                }
                                if (gofp != null) {
                                    candidate.setRemainingDeliveries(gofp.getRemainingDeliveries());
                                }
                                return candidate;
                            })
                            .switchIfEmpty(Mono.defer(() ->
                                    Mono.zip(vehicleMono, occupiedM3).map(t -> {
                                        FreelancerVehicle vehicle = t.getT1();
                                        double occupied = t.getT2();
                                        double trunkTotal = vehicleApplicationService.calculateVolumeInM3(
                                                vehicle.getTrunkLength(), vehicle.getTrunkWidth(),
                                                vehicle.getTrunkHeight(), vehicle.getTrunkDimensionUnit());
                                        candidate.setAvailableTrunkVolumeM3(Math.max(0.0, trunkTotal - occupied));
                                        return candidate;
                                    })))
                            .onErrorResume(e -> {
                                log.warn("[Matching] Could not enrich candidate {}: {}", fId, e.getMessage());
                                candidate.setAvailableTrunkVolumeM3(0.0);
                                return Mono.just(candidate);
                            });
                })
                .collectList();
    }

    private UUID extractAnnouncementId(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String trimmed = payload.trim();
        if (!trimmed.startsWith("{")) {
            try {
                return UUID.fromString(trimmed.replace("\"", ""));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            if (root.hasNonNull("aggregateId")) {
                return UUID.fromString(root.get("aggregateId").asText());
            }
            if (root.has("payload") && root.get("payload").hasNonNull("aggregateId")) {
                return UUID.fromString(root.get("payload").get("aggregateId").asText());
            }
            if (root.hasNonNull("announcementId")) {
                return UUID.fromString(root.get("announcementId").asText());
            }
            if (root.has("announcement") && root.get("announcement").hasNonNull("id")) {
                return UUID.fromString(root.get("announcement").get("id").asText());
            }
        } catch (Exception e) {
            log.debug("[Matching] Failed to parse announcement payload: {}", e.getMessage());
        }
        return null;
    }
}
