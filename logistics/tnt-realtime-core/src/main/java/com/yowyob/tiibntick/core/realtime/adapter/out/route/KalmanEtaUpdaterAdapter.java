package com.yowyob.tiibntick.core.realtime.adapter.out.route;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.realtime.application.port.out.IKalmanEtaUpdater;
import com.yowyob.tiibntick.core.realtime.domain.model.ETAInterval;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;
import com.yowyob.tiibntick.core.realtime.domain.service.MissionTrackingCodeCache;
import com.yowyob.tiibntick.core.route.application.port.in.IUpdateEtaUseCase;
import com.yowyob.tiibntick.core.route.domain.model.EtaResult;
import com.yowyob.tiibntick.core.route.domain.model.GPSMeasurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Outbound adapter implementing {@link IKalmanEtaUpdater}.
 *
 * <p>Delegates the Kalman filter ETA recomputation to tnt-route-core's
 * {@link IUpdateEtaUseCase}, resolved directly via Spring context injection in
 * this monolithic modular deployment (both modules are component-scanned into
 * the same {@code tnt-bootstrap} application context).</p>
 *
 * <p>Requires that Kalman state already exists for the mission (seeded by
 * {@code IUpdateEtaUseCase.computeInitialEta()} when transit starts — see
 * {@code DeliveryLifecycleService.startTransit()}). If no state exists yet
 * (mission not yet bootstrapped, or already completed), {@code updateEta()}
 * errors and the caller ({@code GpsPingProcessor}) skips broadcasting for that
 * ping rather than failing the whole GPS ingestion pipeline.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class KalmanEtaUpdaterAdapter implements IKalmanEtaUpdater {

    private static final Logger log = LoggerFactory.getLogger(KalmanEtaUpdaterAdapter.class);

    private final IUpdateEtaUseCase updateEtaUseCase;
    private final MissionTrackingCodeCache trackingCodeCache;

    public KalmanEtaUpdaterAdapter(IUpdateEtaUseCase updateEtaUseCase,
                                    MissionTrackingCodeCache trackingCodeCache) {
        this.updateEtaUseCase = updateEtaUseCase;
        this.trackingCodeCache = trackingCodeCache;
    }

    @Override
    public Mono<LiveETAUpdate> update(String delivererId, String missionId, String tenantId,
                                      GeoCoordinates coordinates, double speedKmh, double bearing,
                                      double accuracyMetres) {
        log.debug("Triggering Kalman ETA update for mission {} — deliverer {} at {}",
                missionId, delivererId, coordinates);

        String trackingCode = trackingCodeCache.get(missionId);
        GPSMeasurement measurement = new GPSMeasurement(
                GeoPoint.of(coordinates.latitude(), coordinates.longitude()),
                speedKmh, bearing, accuracyMetres, Instant.now());

        return updateEtaUseCase.updateEta(missionId, trackingCode, measurement)
                .map(eta -> toLiveETAUpdate(eta, delivererId, missionId, tenantId, trackingCode, coordinates));
    }

    private LiveETAUpdate toLiveETAUpdate(EtaResult eta, String delivererId, String missionId,
                                          String tenantId, String trackingCode, GeoCoordinates coordinates) {
        ETAInterval interval = ETAInterval.of(
                LocalDateTime.ofInstant(eta.lowerBound(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(eta.upperBound(), ZoneOffset.UTC),
                eta.confidenceLevel());

        return LiveETAUpdate.of(
                missionId, delivererId, tenantId, trackingCode,
                coordinates, interval,
                eta.remainingDistanceKm(),
                (int) Math.max(eta.remainingMinutes(Instant.now()), 0),
                eta.confidenceLevel());
    }
}
