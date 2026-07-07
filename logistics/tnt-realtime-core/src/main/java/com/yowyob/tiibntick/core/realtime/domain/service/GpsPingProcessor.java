package com.yowyob.tiibntick.core.realtime.domain.service;

import com.yowyob.tiibntick.core.realtime.application.port.out.IActorLocationUpdater;
import com.yowyob.tiibntick.core.realtime.application.port.out.IKalmanEtaUpdater;
import com.yowyob.tiibntick.core.realtime.application.port.out.IRealtimeEventPublisher;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.event.GpsPositionUpdatedEvent;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core domain service for GPS stream ingestion and processing.
 *
 * <p>Processing pipeline for each incoming ping:</p>
 * <ol>
 *   <li>Validate the GPS entry (coordinates, accuracy, speed).</li>
 *   <li>Detect outliers using the previous known position.</li>
 *   <li>Update the actor's location via {@link IActorLocationUpdater}.</li>
 *   <li>Trigger the Kalman ETA recomputation via {@link IKalmanEtaUpdater}.</li>
 *   <li>Broadcast the new ETA via {@link IWebSocketBroadcaster}.</li>
 *   <li>Run anomaly detection (trajectory divergence, prolonged stop).</li>
 *   <li>Publish an enriched {@link GpsPositionUpdatedEvent} to Kafka,
 *       including anomaly flags consumed by {@code tnt-incident-core}.</li>
 * </ol>
 *
 * <h3>Anomaly detection added for tnt-incident-core integration:</h3>
 * <ul>
 *   <li><b>Trajectory anomaly</b>: detected when bearing changes by more than
 *       {@value TRAJECTORY_ANOMALY_BEARING_THRESHOLD_DEG} degrees in a single ping
 *       while speed is above {@value TRAJECTORY_ANOMALY_MIN_SPEED_KMH} km/h.
 *       This catches GPS spoofing patterns and sudden unexplained direction changes.</li>
 *   <li><b>Prolonged stop</b>: detected when the deliverer's speed has been below
 *       {@value STOP_SPEED_THRESHOLD_KMH} km/h continuously for more than
 *       {@value PROLONGED_STOP_THRESHOLD_MINUTES} minutes during an active mission.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public class GpsPingProcessor {

    private static final Logger log = LoggerFactory.getLogger(GpsPingProcessor.class);

    // ── Anomaly detection thresholds ──────────────────────────────────────────

    /** Minimum speed (km/h) above which a sharp bearing change is considered a trajectory anomaly. */
    private static final double TRAJECTORY_ANOMALY_MIN_SPEED_KMH = 5.0;

    /** Maximum bearing change (degrees) in a single ping before flagging a trajectory anomaly. */
    private static final double TRAJECTORY_ANOMALY_BEARING_THRESHOLD_DEG = 150.0;

    /** Speed (km/h) below which the deliverer is considered stopped. */
    private static final double STOP_SPEED_THRESHOLD_KMH = 2.0;

    /** Duration (minutes) a deliverer must be stopped before it is flagged as a prolonged stop. */
    private static final int PROLONGED_STOP_THRESHOLD_MINUTES = 10;

    // ── In-memory state ───────────────────────────────────────────────────────

    /** Last-known-position cache for outlier detection (delivererId → last valid entry). */
    private final Map<String, GPSStreamEntry> lastPositions = new ConcurrentHashMap<>();

    /**
     * Stop start time tracker: when a deliverer first went below the stop speed threshold.
     * Key: delivererId, Value: timestamp when the stop was first detected.
     */
    private final Map<String, LocalDateTime> stopStartTimes = new ConcurrentHashMap<>();

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final IActorLocationUpdater locationUpdater;
    private final IKalmanEtaUpdater kalmanEtaUpdater;
    private final IWebSocketBroadcaster broadcaster;
    private final IRealtimeEventPublisher eventPublisher;
    private final GeofenceMonitorService geofenceMonitorService;

    public GpsPingProcessor(IActorLocationUpdater locationUpdater,
                            IKalmanEtaUpdater kalmanEtaUpdater,
                            IWebSocketBroadcaster broadcaster,
                            IRealtimeEventPublisher eventPublisher,
                            GeofenceMonitorService geofenceMonitorService) {
        this.locationUpdater = locationUpdater;
        this.kalmanEtaUpdater = kalmanEtaUpdater;
        this.broadcaster = broadcaster;
        this.eventPublisher = eventPublisher;
        this.geofenceMonitorService = geofenceMonitorService;
    }

    /**
     * Processes a single GPS ping entry through the full pipeline.
     *
     * @param entry the incoming GPS ping
     * @return a Mono completing when all downstream operations are done
     */
    public Mono<Void> process(GPSStreamEntry entry) {
        if (!entry.isValid()) {
            log.warn("Discarding invalid GPS entry from deliverer {} (tenant {}): {}",
                    entry.delivererId(), entry.tenantId(), entry);
            return Mono.empty();
        }

        GPSStreamEntry previous = lastPositions.get(entry.delivererId());
        if (entry.isOutlier(previous)) {
            log.warn("Discarding outlier GPS entry from deliverer {}: distance jump detected",
                    entry.delivererId());
            return Mono.empty();
        }

        // Cache the validated position for future outlier and anomaly detection
        lastPositions.put(entry.delivererId(), entry);

        // Compute anomaly flags before publishing
        boolean trajectoryAnomaly = detectTrajectoryAnomaly(entry, previous);
        int stopDurationMinutes = updateStopTracker(entry);
        boolean prolongedStop = entry.hasMission() && stopDurationMinutes >= PROLONGED_STOP_THRESHOLD_MINUTES;

        if (trajectoryAnomaly) {
            log.warn("Trajectory anomaly detected for deliverer={} mission={} bearing={}",
                    entry.delivererId(), entry.missionId(), entry.bearing());
        }
        if (prolongedStop) {
            log.warn("Prolonged stop detected for deliverer={} mission={} stoppedFor={}min",
                    entry.delivererId(), entry.missionId(), stopDurationMinutes);
        }

        final boolean finalTrajectoryAnomaly = trajectoryAnomaly;
        final boolean finalProlongedStop = prolongedStop;
        final int finalStopDuration = stopDurationMinutes;

        return locationUpdater
                .updateLocation(entry.delivererId(), entry.tenantId(), entry.coordinates())
                .then(processKalmanAndBroadcast(entry))
                .then(geofenceMonitorService.checkGeofences(entry))
                .then(publishGpsEvent(entry, finalTrajectoryAnomaly, finalProlongedStop, finalStopDuration))
                .doOnError(ex -> log.error(
                        "Error processing GPS ping for deliverer={}: {}",
                        entry.delivererId(), ex.getMessage(), ex));
    }

    /**
     * Clears the last known position and stop tracker for a deliverer.
     * Should be called when a mission ends or the deliverer disconnects.
     *
     * @param delivererId the deliverer whose state to clear
     */
    public void clearLastPosition(String delivererId) {
        lastPositions.remove(delivererId);
        stopStartTimes.remove(delivererId);
    }

    // ── Private pipeline steps ───────────────────────────────────────────────

    private Mono<Void> processKalmanAndBroadcast(GPSStreamEntry entry) {
        if (!entry.hasMission()) {
            return Mono.empty();
        }

        return kalmanEtaUpdater
                .update(entry.delivererId(), entry.missionId(), entry.tenantId(),
                        entry.coordinates(), entry.speedKmh(), entry.bearing())
                .flatMap(this::broadcastEtaUpdate)
                .onErrorResume(ex -> {
                    log.warn("Kalman ETA update failed for mission={}: {}",
                            entry.missionId(), ex.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> broadcastEtaUpdate(LiveETAUpdate etaUpdate) {
        BroadcastTopic topic = BroadcastTopic.forDelivery(etaUpdate.missionId());
        Mono<Void> deliveryBroadcast = broadcaster.broadcast(topic, etaUpdate);
        Mono<Void> trackingBroadcast = etaUpdate.trackingCode() != null
                ? broadcaster.broadcast(BroadcastTopic.forTracking(etaUpdate.trackingCode()), etaUpdate)
                : Mono.empty();
        return Mono.when(deliveryBroadcast, trackingBroadcast);
    }

    /**
     * Publishes the enriched {@link GpsPositionUpdatedEvent} to Kafka.
     * The event includes anomaly detection flags required by tnt-incident-core.
     *
     * @param entry              validated GPS ping entry
     * @param trajectoryAnomaly  whether a trajectory anomaly was detected
     * @param prolongedStop      whether the deliverer has been stopped too long
     * @param stopDurationMin    how many minutes the deliverer has been stopped
     * @return Mono completing when the Kafka message is acknowledged
     */
    private Mono<Void> publishGpsEvent(GPSStreamEntry entry,
                                        boolean trajectoryAnomaly,
                                        boolean prolongedStop,
                                        int stopDurationMin) {
        GpsPositionUpdatedEvent event = new GpsPositionUpdatedEvent(
                entry.tenantId(),
                entry.delivererId(),
                entry.missionId(),
                null, // agencyId: resolved by tnt-actor-core from delivererId; not available here
                entry.coordinates(),
                entry.speedKmh(),
                entry.bearing(),
                entry.accuracy(),
                trajectoryAnomaly,
                prolongedStop,
                stopDurationMin
        );
        return eventPublisher.publish(event);
    }

    // ── Anomaly detection helpers ─────────────────────────────────────────────

    /**
     * Detects whether the deliverer's trajectory shows a significant anomaly.
     *
     * <p>Anomaly criteria (all must be true simultaneously):
     * <ul>
     *   <li>Previous position exists</li>
     *   <li>Current speed above {@value TRAJECTORY_ANOMALY_MIN_SPEED_KMH} km/h</li>
     *   <li>Bearing change exceeds {@value TRAJECTORY_ANOMALY_BEARING_THRESHOLD_DEG} degrees</li>
     * </ul>
     *
     * <p>A bearing change threshold of 150° catches U-turns and GPS spoofing
     * while tolerating normal manoeuvres (roundabouts, sharp turns).</p>
     *
     * @param current  current GPS ping
     * @param previous previous valid GPS ping (may be null for first ping)
     * @return true if a trajectory anomaly is detected
     */
    private boolean detectTrajectoryAnomaly(GPSStreamEntry current, GPSStreamEntry previous) {
        if (previous == null) return false;
        if (current.speedKmh() < TRAJECTORY_ANOMALY_MIN_SPEED_KMH) return false;

        double bearingDelta = Math.abs(current.bearing() - previous.bearing());
        // Normalize to [0, 180] — bearing is circular
        if (bearingDelta > 180.0) {
            bearingDelta = 360.0 - bearingDelta;
        }

        return bearingDelta > TRAJECTORY_ANOMALY_BEARING_THRESHOLD_DEG;
    }

    /**
     * Updates the stop duration tracker for a deliverer and returns the current
     * stop duration in minutes.
     *
     * <p>Logic:
     * <ul>
     *   <li>If current speed < {@value STOP_SPEED_THRESHOLD_KMH}: record the stop start
     *       time (if not already recorded) and return elapsed minutes.</li>
     *   <li>If current speed ≥ {@value STOP_SPEED_THRESHOLD_KMH}: clear the stop timer
     *       and return 0.</li>
     * </ul>
     *
     * @param entry current GPS ping
     * @return stop duration in minutes (0 if deliverer is moving)
     */
    private int updateStopTracker(GPSStreamEntry entry) {
        String delivererId = entry.delivererId();

        if (entry.speedKmh() < STOP_SPEED_THRESHOLD_KMH) {
            // Deliverer is (still) stopped — record start if not yet tracked
            LocalDateTime stopStart = stopStartTimes.computeIfAbsent(
                    delivererId, k -> entry.timestamp());

            long elapsedMinutes = Duration.between(stopStart, entry.timestamp()).toMinutes();
            return (int) Math.max(0, elapsedMinutes);
        } else {
            // Deliverer is moving — clear stop timer
            stopStartTimes.remove(delivererId);
            return 0;
        }
    }
}
