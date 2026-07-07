package com.yowyob.tiibntick.core.realtime.application.port.out;

import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;
import reactor.core.publisher.Mono;

/**
 * Outbound port for invoking the Kalman filter ETA computation in tnt-route-core.
 * Called after each valid GPS ping for a mission in progress.
 *
 * @author MANFOUO Braun
 */
public interface IKalmanEtaUpdater {

    /**
     * Triggers the Kalman filter state update in tnt-route-core with the latest
     * GPS position and returns the recomputed ETA.
     *
     * @param delivererId the deliverer's identifier
     * @param missionId   the active mission identifier
     * @param tenantId    the tenant context
     * @param coordinates the latest GPS coordinates
     * @param speedKmh    the measured speed in km/h
     * @param bearing     the measured bearing in degrees [0, 360]
     * @return Mono with the updated ETA, or empty if no route is active
     */
    Mono<LiveETAUpdate> update(String delivererId, String missionId, String tenantId,
                               GeoCoordinates coordinates, double speedKmh, double bearing);
}
