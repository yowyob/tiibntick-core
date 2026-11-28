package com.yowyob.tiibntick.core.delivery.application.service.watch;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort.Alerts;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryTransitionExecutor;
import com.yowyob.tiibntick.core.delivery.config.DeliveryAlertProperties;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryAlertType;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Delivery alerts (lot L3, spec §3.5, decisions [C.3], [D.1], QO-18): detections raise {@code DeliveryAlertRaised} /
 * {@code DeliveryAlertCleared} on {@code tnt.delivery.events} and go to the support. They never open an incident: only
 * the assigned courier declares one. Replaces the detection listeners of incident-core
 * ({@code IncidentEventConsumer}, removed).
 *
 * <ul>
 *   <li>{@link #evaluate(Delivery, Alerts, Instant)}: time-based alerts ({@code PICKUP_LATE}, {@code DELIVERY_LATE},
 *       {@code ETA_EXCEEDED}, {@code GPS_SIGNAL_LOST}), run by {@code DeliveryLateScheduler};</li>
 *   <li>{@link #onPosition}: GPS stream ({@code TRAJECTORY_DEVIATION}, {@code PROLONGED_STOP}, GPS back);</li>
 *   <li>{@link #onGeofence}: danger or restricted zone crossing ({@code GEOFENCE_EXIT}).</li>
 * </ul>
 *
 * <p>Thresholds: those of the delivery's organisation (administration module, QO-18, D-131 to D-133), platform values
 * when the delivery has no agency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryWatchService {

    /** A GPS position without alert change is written at most this often (the GPS-lost threshold is minutes). */
    static final java.time.Duration POSITION_WRITE_INTERVAL = java.time.Duration.ofSeconds(60);

    /** The courier carries the parcel: ETA and GPS apply. */
    static final Set<DeliveryStatus> MOVING = EnumSet.of(DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT,
            DeliveryStatus.OUT_FOR_DELIVERY);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryTransitionExecutor transitionExecutor;
    private final DeliveryAlertProperties properties;
    private final DeliveryThresholdsPort thresholds;

    /**
     * Evaluates the time-based alerts of the watched deliveries and saves those that changed. A delivery that fails
     * to save does not stop the others; the run fails at the end so that the job reports it.
     *
     * @return the number of deliveries with a new alert
     */
    public Mono<Long> scan(Instant now) {
        return deliveryRepository.findWatchCandidates(properties.getBatchSize())
                .concatMap(delivery -> alertsOf(delivery).flatMap(alerts -> evaluate(delivery, alerts, now)
                        ? transitionExecutor.saveAndPublish(delivery).thenReturn(1L)
                        : Mono.just(0L)))
                .reduce(0L, Long::sum);
    }

    /**
     * Raises the time-based alerts due on {@code delivery} (pure; nothing saved).
     *
     * @return whether at least one alert was raised
     */
    public boolean evaluate(Delivery delivery, Alerts alerts, Instant now) {
        boolean changed = false;
        DeliveryStatus status = delivery.getStatus();

        if (status == DeliveryStatus.ASSIGNED && delivery.getPickupWindowEnd() != null) {
            Instant threshold = delivery.getPickupWindowEnd().plus(alerts.pickupLateGrace());
            if (now.isAfter(threshold)) {
                changed |= delivery.raiseAlert(DeliveryAlertType.PICKUP_LATE, now, threshold, now);
            }
        }

        Instant deadline = deliveryDeadline(delivery, alerts);
        if (deadline != null && now.isAfter(deadline)) {
            changed |= delivery.raiseAlert(DeliveryAlertType.DELIVERY_LATE, now, deadline, now);
        }

        if (MOVING.contains(status) && delivery.getCurrentEta() != null
                && delivery.getCurrentEta().estimatedArrival() != null) {
            Instant threshold = delivery.getCurrentEta().estimatedArrival().plus(alerts.etaExceededGrace());
            if (now.isAfter(threshold)) {
                changed |= delivery.raiseAlert(DeliveryAlertType.ETA_EXCEEDED, now, threshold, now);
            }
        }

        if (MOVING.contains(status) && delivery.getLastPositionAt() != null
                && now.isAfter(delivery.getLastPositionAt().plus(alerts.gpsSignalLost()))) {
            changed |= delivery.raiseAlert(DeliveryAlertType.GPS_SIGNAL_LOST, delivery.getLastPositionAt(),
                    alerts.gpsSignalLost(), now);
        }
        return changed;
    }

    /**
     * Delivery deadline (QO-18, D-132): end of the delivery window + grace, else the creation + the delay of the
     * urgency.
     */
    static Instant deliveryDeadline(Delivery delivery, Alerts alerts) {
        if (delivery.getDeliveryWindowEnd() != null) {
            return delivery.getDeliveryWindowEnd().plus(alerts.deliveryLateGrace());
        }
        Instant created = delivery.getCreatedAt();
        return created == null ? null : created.plus(alerts.deliveryLateFallback(delivery.getUrgency()));
    }

    /** Alert thresholds of the delivery's organisation; platform defaults if they cannot be read. */
    private Mono<Alerts> alertsOf(Delivery delivery) {
        return thresholds.forAgency(delivery.getAgencyId())
                .map(DeliveryThresholdsPort.Thresholds::alerts)
                .onErrorResume(e -> {
                    log.warn("Thresholds of delivery {} unreadable, platform defaults used: {}", delivery.getId(),
                            e.getMessage());
                    return Mono.just(Alerts.DEFAULT);
                })
                .defaultIfEmpty(Alerts.DEFAULT);
    }

    /**
     * A GPS position of the courier of {@code deliveryId}: records it, clears {@code GPS_SIGNAL_LOST}, and raises or
     * clears the anomalies flagged by the realtime stream.
     */
    public Mono<Void> onPosition(UUID tenantId, UUID deliveryId, Instant at, boolean trajectoryAnomaly,
                                 boolean prolongedStop) {
        return load(tenantId, deliveryId).flatMap(delivery -> delivery.getStatus().isTerminal() ? Mono.<Void>empty()
                : alertsOf(delivery).flatMap(alerts -> onPosition(delivery, alerts, at, trajectoryAnomaly,
                prolongedStop)));
    }

    private Mono<Void> onPosition(Delivery delivery, Alerts alerts, Instant at, boolean trajectoryAnomaly,
                              boolean prolongedStop) {
        Instant now = Instant.now();
        Instant previous = delivery.getLastPositionAt();
        Instant position = at != null ? at : now;
        delivery.recordPosition(position);
        boolean changed = delivery.clearAlert(DeliveryAlertType.GPS_SIGNAL_LOST, now);
        changed |= trajectoryAnomaly
                ? delivery.raiseAlert(DeliveryAlertType.TRAJECTORY_DEVIATION, null, null, now)
                : delivery.clearAlert(DeliveryAlertType.TRAJECTORY_DEVIATION, now);
        changed |= prolongedStop
                ? delivery.raiseAlert(DeliveryAlertType.PROLONGED_STOP, null, alerts.prolongedStop(), now)
                : delivery.clearAlert(DeliveryAlertType.PROLONGED_STOP, now);
        // a position alone is stored at most once per POSITION_WRITE_INTERVAL (GPS sends every few seconds)
        if (!changed && previous != null && position.isBefore(previous.plus(POSITION_WRITE_INTERVAL))) {
            return Mono.<Void>empty();
        }
        return transitionExecutor.saveAndPublish(delivery).then();
    }

    /** The courier of {@code deliveryId} crossed a danger or restricted zone ({@code GEOFENCE_EXIT}). */
    public Mono<Void> onGeofence(UUID tenantId, UUID deliveryId, String zoneType) {
        return load(tenantId, deliveryId).flatMap(delivery ->
                delivery.raiseAlert(DeliveryAlertType.GEOFENCE_EXIT, zoneType, null, Instant.now())
                        ? transitionExecutor.saveAndPublish(delivery).then()
                        : Mono.<Void>empty());
    }

    /** An unknown delivery is ignored (positions of a courier outside a TiiBnTick delivery). */
    private Mono<Delivery> load(UUID tenantId, UUID deliveryId) {
        Mono<Delivery> found = tenantId != null
                ? deliveryRepository.findById(tenantId, deliveryId)
                : deliveryRepository.findByIdNoTenant(deliveryId);
        return found.switchIfEmpty(Mono.defer(() -> {
            log.debug("Watch event for unknown delivery {} ignored", deliveryId);
            return Mono.empty();
        }));
    }
}
