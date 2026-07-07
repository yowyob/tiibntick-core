package com.yowyob.tiibntick.core.realtime.adapter.out.route;

import com.yowyob.tiibntick.core.realtime.application.port.out.IKalmanEtaUpdater;
import com.yowyob.tiibntick.core.realtime.domain.model.ETAInterval;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Outbound adapter implementing {@link IKalmanEtaUpdater}.
 *
 * <p>Delegates the Kalman filter ETA recomputation to tnt-route-core.
 * In the monolithic modular deployment, tnt-route-core's Kalman service bean
 * is resolved directly via Spring context injection (configured in RealtimeCoreConfig).</p>
 *
 * <p>The Kalman filter in tnt-route-core uses an Extended Kalman Filter (EKF) with
 * state vector [position, speed, measurement_bias]. This adapter converts the
 * realtime-core GPS data types to the route-core's input types and maps
 * the result back to {@link LiveETAUpdate}.</p>
 *
 * <p>In a future microservices migration, this adapter calls the tnt-route-core
 * gRPC/REST Kalman endpoint instead of direct Java invocation.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class KalmanEtaUpdaterAdapter implements IKalmanEtaUpdater {

    private static final Logger log = LoggerFactory.getLogger(KalmanEtaUpdaterAdapter.class);

    /**
     * The tnt-route-core Kalman update service is injected at runtime by
     * {@code RealtimeCoreConfig.kalmanEtaUpdater()} using Spring's optional
     * bean resolution. If tnt-route-core beans are not present, a default
     * passthrough implementation is used.
     */
    public KalmanEtaUpdaterAdapter() {
        // Actual route-core service injected via RealtimeCoreConfig
    }

    @Override
    public Mono<LiveETAUpdate> update(String delivererId, String missionId, String tenantId,
                                      GeoCoordinates coordinates, double speedKmh, double bearing) {
        log.debug("Triggering Kalman ETA update for mission {} — deliverer {} at {}",
                missionId, delivererId, coordinates);

        // In the current monolithic modular architecture, this call is replaced
        // by direct injection of tnt-route-core's KalmanStateUpdateService.
        // The stub below demonstrates the contract and return type.
        // RealtimeCoreConfig.kalmanEtaUpdater() replaces this bean with the real implementation.
        return Mono.defer(() -> {
            // Stub: returns a conservative ETA estimate
            // Real implementation: calls tnt-route-core's KalmanStateUpdateService
            LocalDateTime estimatedArrival = LocalDateTime.now().plusMinutes(30);
            ETAInterval interval = ETAInterval.of(
                    estimatedArrival.minusMinutes(5),
                    estimatedArrival.plusMinutes(5),
                    0.90
            );

            LiveETAUpdate etaUpdate = LiveETAUpdate.of(
                    missionId, delivererId, tenantId, null,
                    coordinates, interval,
                    15.0, 30, 0.90
            );

            log.trace("Kalman ETA stub result for mission {}: ETA={}", missionId, etaUpdate.bestEta());
            return Mono.just(etaUpdate);
        });
    }

    /**
     * Internal DTO for Kalman update request to tnt-route-core.
     */
    record KalmanUpdateRequest(
            String delivererId,
            String missionId,
            String tenantId,
            double latitude,
            double longitude,
            double speedKmh,
            double bearing,
            long observationTimestampMs
    ) {}

    /**
     * Internal DTO for Kalman update response from tnt-route-core.
     */
    record KalmanUpdateResponse(
            String missionId,
            String trackingCode,
            double etaLowerBoundEpochSeconds,
            double etaMidpointEpochSeconds,
            double etaUpperBoundEpochSeconds,
            double kalmanConfidence,
            double remainingDistanceKm,
            int remainingTimeMin
    ) {}
}
