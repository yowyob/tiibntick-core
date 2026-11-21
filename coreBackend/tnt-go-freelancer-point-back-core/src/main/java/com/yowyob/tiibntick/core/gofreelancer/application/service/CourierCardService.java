package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpCourierPricingProperties;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerVehicle;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lot C-54 — what a sender sees of a courier (A4) and the loyalty the server ranks him by (A5).
 *
 * <p>The card carries the courier's real vehicle (type, plate, photos), his number of parcels
 * delivered and a badge. The <strong>profile creation date never leaves the server</strong>: it only
 * feeds {@link Card#loyaltyScore()} and the {@code LOYAL} badge.
 *
 * <ul>
 *   <li>badge {@code NEW}: fewer than {@code new-below-deliveries} (5) parcels delivered;</li>
 *   <li>{@code CONFIRMED}: at least {@code confirmed-from-deliveries} (5 — lot C-54b, no gap after NEW);</li>
 *   <li>{@code LOYAL}: at least {@code loyal-from-deliveries} (50) <em>and</em> a profile at least
 *       {@code loyal-from-months} (6) months old;</li>
 *   <li>no badge only if the thresholds are configured with a gap ({@code confirmed} above {@code new}).</li>
 * </ul>
 *
 * <p>Loyalty score, in [0, 1]: half volume ({@code delivered / score-deliveries-cap}, capped at 1),
 * half tenure ({@code days / score-tenure-days-cap}, capped at 1). Bounded, so it can only weigh as
 * much as its TOPSIS weight lets it — never as much as the distance.
 */
@Slf4j
@Service
public class CourierCardService {

    public static final String BADGE_NEW = "NEW";
    public static final String BADGE_CONFIRMED = "CONFIRMED";
    public static final String BADGE_LOYAL = "LOYAL";

    private final GofpFreelancerRepository freelancers;
    private final FreelancerVehicleRepository vehicles;
    private final DeliveryRepository deliveries;
    private final GofpCourierPricingProperties properties;
    private Clock clock = Clock.systemUTC();

    @Autowired
    public CourierCardService(GofpFreelancerRepository freelancers,
                              FreelancerVehicleRepository vehicles,
                              DeliveryRepository deliveries,
                              GofpCourierPricingProperties properties) {
        this.freelancers = freelancers;
        this.vehicles = vehicles;
        this.deliveries = deliveries;
        this.properties = properties != null ? properties : new GofpCourierPricingProperties();
    }

    /** Tests pin the date the tenure is measured at. */
    public CourierCardService withClock(Clock clock) {
        this.clock = clock;
        return this;
    }

    /**
     * @param vehicleType     canonical core type (MOTORBIKE, TRICYCLE, CAR, TRUCK, BUS…), or null
     * @param vehicleCategory app word (moto, tricycle, taxi, camion, bus), or null when unknown
     * @param loyaltyScore    server-side only — never serialised into a sender-facing DTO
     */
    public record Card(String vehicleType, String vehicleCategory, String vehiclePlate,
                       List<String> vehiclePhotoUrls, long deliveredCount, String badge,
                       double loyaltyScore) {

        public static final Card UNKNOWN = new Card(null, null, null, List.of(), 0, BADGE_NEW, 0.0);
    }

    /** Card of a courier addressed by any of his ids (gofp id or core freelancer id). */
    public Mono<Card> cardOfWireId(UUID wireId) {
        if (wireId == null) return Mono.just(Card.UNKNOWN);
        return freelancers.findById(wireId)
                .switchIfEmpty(Mono.defer(() -> freelancers.findByCoreFreelancerId(wireId)))
                .flatMap(this::cardOf)
                .switchIfEmpty(Mono.defer(() -> cardOfIds(List.of(wireId), null, null)))
                .onErrorResume(e -> {
                    log.warn("Courier card of {} unavailable: {}", wireId, e.getMessage());
                    return Mono.just(Card.UNKNOWN);
                });
    }

    public Mono<Card> cardOf(GofpFreelancer f) {
        if (f == null) return Mono.just(Card.UNKNOWN);
        List<UUID> ids = new ArrayList<>();
        ids.add(f.getId());
        if (f.getCoreFreelancerId() != null && !f.getCoreFreelancerId().equals(f.getId())) {
            ids.add(f.getCoreFreelancerId());
        }
        return cardOfIds(ids, f.getCreatedAt(), f);
    }

    private Mono<Card> cardOfIds(List<UUID> ids, Instant createdAt, GofpFreelancer f) {
        Mono<FreelancerVehicle> vehicle = vehicles.findByFreelancerId(ids.get(0));
        if (ids.size() > 1) {
            vehicle = vehicle.switchIfEmpty(Mono.defer(() -> vehicles.findByFreelancerId(ids.get(1))));
        }
        Mono<FreelancerVehicle> v = vehicle.defaultIfEmpty(new FreelancerVehicle())
                .onErrorReturn(new FreelancerVehicle());
        Mono<Long> delivered = deliveries == null ? Mono.just(0L)
                : deliveries.countDeliveredByFreelancerIds(ids).defaultIfEmpty(0L).onErrorReturn(0L);
        return Mono.zip(v, delivered).map(t -> build(t.getT1(), t.getT2(), createdAt));
    }

    Card build(FreelancerVehicle v, long delivered, Instant createdAt) {
        VehicleCategory category = VehicleCategory.parse(v.getVehicleType());
        String canonical = null;
        if (category != null) {
            String raw = v.getVehicleType().trim().toUpperCase(java.util.Locale.ROOT);
            canonical = java.util.Arrays.stream(
                            com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleType.values())
                    .anyMatch(t -> t.name().equals(raw)) ? raw : category.canonical().name();
        }
        Instant now = clock.instant();
        GofpCourierPricingProperties.Loyalty l = properties.getLoyalty();
        return new Card(canonical, category == null ? null : category.appWord(), v.getPlateNumber(),
                v.photoUrlList(), delivered, badge(delivered, createdAt, l, now),
                loyaltyScore(delivered, createdAt, l, now));
    }

    // ── rules (pure, tested directly) ───────────────────────────────────

    public static String badge(long delivered, Instant createdAt, GofpCourierPricingProperties.Loyalty l,
                               Instant now) {
        if (delivered >= l.getLoyalFromDeliveries() && createdAt != null
                && !createdAt.atOffset(ZoneOffset.UTC).plusMonths(l.getLoyalFromMonths()).toInstant().isAfter(now)) {
            return BADGE_LOYAL;
        }
        if (delivered >= l.getConfirmedFromDeliveries()) return BADGE_CONFIRMED;
        if (delivered < l.getNewBelowDeliveries()) return BADGE_NEW;
        return null;
    }

    public static double loyaltyScore(long delivered, Instant createdAt, GofpCourierPricingProperties.Loyalty l,
                                      Instant now) {
        double volume = l.getScoreDeliveriesCap() <= 0 ? 0
                : Math.min(1.0, Math.max(0, delivered) / (double) l.getScoreDeliveriesCap());
        double tenure = 0;
        if (createdAt != null && l.getScoreTenureDaysCap() > 0) {
            long days = Math.max(0, Duration.between(createdAt, now).toDays());
            tenure = Math.min(1.0, days / (double) l.getScoreTenureDaysCap());
        }
        return 0.5 * volume + 0.5 * tenure;
    }
}
