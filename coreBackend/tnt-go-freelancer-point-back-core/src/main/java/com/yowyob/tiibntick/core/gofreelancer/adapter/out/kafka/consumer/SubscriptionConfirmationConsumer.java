package com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.consumer;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.SubscriptionAttemptEvent;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerQuotaService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerVehicleApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase.FreelancerCandidate;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.AnnouncementSubscription;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerVehicle;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.AnnouncementSubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound Kafka adapter: consumes "subscription-attempts" events.
 *
 * <p>Mirrors ATANGA's {@code SubscriptionConfirmationConsumer} — adapted to the
 * hexagonal architecture of tnt-go-freelancer-point-back-core.
 *
 * <p>Flow per event:
 * <ol>
 *   <li>Check the announcement is still PUBLISHED.</li>
 *   <li>Check the freelancer has a valid quota ({@link FreelancerQuotaService}).</li>
 *   <li>Guard against duplicate subscriptions.</li>
 *   <li>Persist the {@link AnnouncementSubscription} with status {@code REGISTERED}.</li>
 *   <li>Re-rank ALL current subscribers via TOPSIS and notify the client.</li>
 * </ol>
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionConfirmationConsumer {

    private final IAnnouncementRepository announcementRepository;
    private final AnnouncementSubscriptionRepository subscriptionRepository;
    private final GofpFreelancerRepository freelancerRepository;
    private final FreelancerVehicleRepository vehicleRepository;
    private final DeliveryRepository deliveryRepository;
    private final FreelancerVehicleApplicationService vehicleApplicationService;
    private final FreelancerQuotaService quotaService;
    private final TopsisRankingUseCase topsisRankingUseCase;
    private final PushNotificationPort pushNotificationPort;

    // ── Kafka consumer ────────────────────────────────────────────────────────

    @KafkaListener(topics = "subscription-attempts", groupId = "tnt-go-freelancer-core-subscription-group")
    public void consumeSubscriptionAttempt(SubscriptionAttemptEvent event) {
        log.info("[SubConfirm] Subscription attempt — announcementId={} freelancerId={}",
                event.getAnnouncementId(), event.getFreelancerId());

        process(event)
                .doOnError(e -> log.error("[SubConfirm] Error processing subscription attempt for announcement {}: {}",
                        event.getAnnouncementId(), e.getMessage(), e))
                .subscribe();
    }

    // ── Processing pipeline ───────────────────────────────────────────────────

    private Mono<Void> process(SubscriptionAttemptEvent event) {
        UUID announcementId = event.getAnnouncementId();
        UUID freelancerId   = event.getFreelancerId();

        return announcementRepository.findById(announcementId)
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("[SubConfirm] Announcement {} not found — ignoring attempt", announcementId)))
                .flatMap(announcement -> {

                    // 1. Announcement must still be PUBLISHED
                    if (announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
                        log.warn("[SubConfirm] Rejected — announcement {} is {} (not PUBLISHED)",
                                announcementId, announcement.getStatus());
                        return Mono.empty();
                    }

                    // 2. Freelancer must have remaining quota
                    return quotaService.hasRemainingQuota(freelancerId)
                            .flatMap(eligible -> {
                                if (!eligible) {
                                    log.warn("[SubConfirm] Rejected — freelancer {} has no valid quota", freelancerId);
                                    return Mono.empty();
                                }

                                // 3. Guard against duplicates
                                return subscriptionRepository
                                        .findByAnnouncementIdAndFreelancerId(announcementId, freelancerId)
                                        .flatMap(existing -> {
                                            log.info("[SubConfirm] Freelancer {} already subscribed to announcement {} — ignored",
                                                    freelancerId, announcementId);
                                            return Mono.<Void>empty();
                                        })
                                        .switchIfEmpty(Mono.defer(() -> {
                                            // 4. Persist the subscription
                                            AnnouncementSubscription sub = new AnnouncementSubscription();
                                            sub.setId(UUID.randomUUID());
                                            sub.setAnnouncementId(announcementId);
                                            sub.setFreelancerId(freelancerId);
                                            sub.setStatus("REGISTERED");
                                            sub.setCreatedAt(Instant.now());

                                            return subscriptionRepository.save(sub)
                                                    .doOnSuccess(saved -> log.info(
                                                            "[SubConfirm] Subscription REGISTERED — freelancer {} → announcement {}",
                                                            freelancerId, announcementId))
                                                    .onErrorResume(DuplicateKeyException.class, e -> {
                                                        log.info("[SubConfirm] Duplicate subscription (race condition) — ignoring");
                                                        return Mono.empty();
                                                    })
                                                    // 5. Re-rank all subscribers and notify client
                                                    .flatMap(saved -> rerankAndNotifyClient(
                                                            announcementId,
                                                            announcement.getClientId(),
                                                            announcement.getRequiredVehicleType()));
                                        }));
                            });
                });
    }

    // ── Re-ranking ────────────────────────────────────────────────────────────

    /**
     * Fetches all current subscribers for the announcement, builds a
     * {@link FreelancerCandidate} list (with real rating + vehicleType),
     * runs TOPSIS (with optional vehicle-type filter), then notifies the client.
     *
     * @param announcementId      the announcement UUID
     * @param clientId            client who owns the announcement (notification target)
     * @param requiredVehicleType vehicle type required by the client, or null
     */
    private Mono<Void> rerankAndNotifyClient(UUID announcementId, UUID clientId,
                                              String requiredVehicleType) {

        return subscriptionRepository.findAllByAnnouncementId(announcementId)
                .flatMap(sub -> buildCandidate(sub.getFreelancerId()))
                .collectList()
                .flatMap(candidates -> {
                    if (candidates.isEmpty()) {
                        log.info("[SubConfirm] No candidates to rank for announcement {}", announcementId);
                        return Mono.empty();
                    }

                    // Use the first candidate's location as the pickup reference
                    // (the real pickup coordinates will be injected when the spatial
                    //  provider is wired — for now we use the freelancer's GPS position)
                    double refLat = candidates.get(0).getLatitude();
                    double refLon = candidates.get(0).getLongitude();

                    return topsisRankingUseCase.rankCandidates(
                                    candidates, refLat, refLon, requiredVehicleType)
                            .flatMap(ranked -> {
                                if (ranked.isEmpty()) {
                                    log.info("[SubConfirm] No candidates survive vehicle-type filter {} for announcement {}",
                                            requiredVehicleType, announcementId);
                                    return Mono.empty();
                                }

                                log.info("[SubConfirm] TOPSIS ranked {} candidates for announcement {} (vehicleType filter: {})",
                                        ranked.size(), announcementId, requiredVehicleType);

                                // Build notification message (same style as ATANGA)
                                String rankedIds = ranked.stream()
                                        .map(c -> c.getFreelancerId().toString().substring(0, 8))
                                        .reduce((a, b) -> a + ", " + b)
                                        .orElse("none");

                                String typeInfo = requiredVehicleType != null
                                        ? " (filtre véhicule : " + requiredVehicleType + ")" : "";

                                String message = ranked.size()
                                        + " livreur(s) ont candidaté pour votre annonce"
                                        + typeInfo
                                        + ". Classement actuel (meilleur en premier) : "
                                        + rankedIds
                                        + ". Consultez la liste pour choisir.";

                                if (clientId == null) {
                                    log.warn("[SubConfirm] Announcement {} has no clientId — cannot notify", announcementId);
                                    return Mono.empty();
                                }

                                return pushNotificationPort.sendPushNotification(
                                        clientId,
                                        "Nouveau candidat pour votre annonce",
                                        message);
                            });
                });
    }

    // ── Candidate builder ─────────────────────────────────────────────────────

    /**
     * Builds a fully enriched {@link FreelancerCandidate} from a freelancer UUID:
     * <ul>
     *   <li>GPS position + real rating + totalDeliveries from {@link GofpFreelancer}</li>
     *   <li>{@code availableTrunkVolumeM3} = trunkTotal(m³) − occupiedByActiveDeliveries(m³)
     *       using the same logic as {@code MatchingUseCase#enrichWithTrunkVolume}:
     *       <ul>
     *         <li>trunkTotal = vehicle.trunkLength × width × height converted via
     *             {@link FreelancerVehicleApplicationService#calculateVolumeInM3}</li>
     *         <li>occupied = SUM(packet.length × width × height) for deliveries
     *             in PICKED_UP / IN_TRANSIT (joined via delivery_needs)</li>
     *       </ul>
     *   </li>
     *   <li>{@code vehicleType} — left null until tnt-resource-core client is wired;
     *       TOPSIS handles null gracefully (score 0.0).</li>
     * </ul>
     */
    private Mono<FreelancerCandidate> buildCandidate(UUID freelancerId) {

        Mono<GofpFreelancer> freelancerMono =
                freelancerRepository.findById(freelancerId)
                        .switchIfEmpty(freelancerRepository.findByCoreFreelancerId(freelancerId));

        Mono<FreelancerVehicle> vehicleMono =
                vehicleRepository.findByFreelancerId(freelancerId)
                        .defaultIfEmpty(new FreelancerVehicle());

        Mono<Double> occupiedM3 =
                deliveryRepository.sumOccupiedVolumeM3ByFreelancerId(freelancerId)
                        .defaultIfEmpty(0.0);

        return Mono.zip(freelancerMono, vehicleMono, occupiedM3)
                .map(t -> {
                    GofpFreelancer f  = t.getT1();
                    FreelancerVehicle v = t.getT2();
                    double occupied   = t.getT3();

                    double lat = f.getLatitudeGps()  != null ? f.getLatitudeGps()  : 0.0;
                    double lon = f.getLongitudeGps() != null ? f.getLongitudeGps() : 0.0;

                    // Available trunk capacity in m³
                    double trunkTotal = vehicleApplicationService.calculateVolumeInM3(
                            v.getTrunkLength(), v.getTrunkWidth(),
                            v.getTrunkHeight(), v.getTrunkDimensionUnit());
                    double available = Math.max(0.0, trunkTotal - occupied);

                    log.debug("[SubConfirm] Candidate {} — trunkTotal={} m³, occupied={} m³, available={} m³",
                            freelancerId, trunkTotal, occupied, available);

                    return FreelancerCandidate.builder()
                            .freelancerId(freelancerId)
                            .latitude(lat)
                            .longitude(lon)
                            .rating(f.getRating())
                            .totalDeliveries(f.getTotalDeliveries())
                            .vehicleType(null) // resolved when tnt-resource-core client is wired
                            .availableTrunkVolumeM3(available)
                            .build();
                })
                .onErrorResume(e -> {
                    log.warn("[SubConfirm] Could not build candidate for freelancer {}: {}", freelancerId, e.getMessage());
                    return Mono.empty();
                });
    }
}
