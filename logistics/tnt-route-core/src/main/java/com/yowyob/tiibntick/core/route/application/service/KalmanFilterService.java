package com.yowyob.tiibntick.core.route.application.service;

import com.yowyob.tiibntick.core.route.application.port.in.IUpdateEtaUseCase;
import com.yowyob.tiibntick.core.route.application.port.out.IKalmanStateRepository;
import com.yowyob.tiibntick.core.route.application.port.out.IRouteEventPublisher;
import com.yowyob.tiibntick.core.route.domain.event.EtaUpdatedEvent;
import com.yowyob.tiibntick.core.route.domain.model.EtaResult;
import com.yowyob.tiibntick.core.route.domain.model.GPSMeasurement;
import com.yowyob.tiibntick.core.route.domain.model.KalmanState;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class KalmanFilterService implements IUpdateEtaUseCase {

    private static final double LAMBDA = 1.5;
    private static final double Q_DISTANCE = 0.1;
    private static final double Q_SPEED = 1.0;
    private static final double Q_BIAS = 0.01;

    private static final double R_DIST_GOOD_GPS = 0.01;
    private static final double R_SPEED_GOOD_GPS = 4.0;
    private static final double R_DIST_POOR_GPS = 0.25;
    private static final double R_SPEED_POOR_GPS = 25.0;

    private static final double ACCURACY_THRESHOLD_METRES = 50.0;

    private final IKalmanStateRepository stateRepository;
    private final IRouteEventPublisher eventPublisher;

    public KalmanFilterService(IKalmanStateRepository stateRepository,
                               IRouteEventPublisher eventPublisher) {
        this.stateRepository = stateRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<EtaResult> updateEta(String missionId, GPSMeasurement measurement) {
        return stateRepository.findByMissionId(missionId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "No Kalman state for mission: " + missionId)))
                .flatMap(state -> {
                    double dt = computeDeltaTimeSeconds(state, measurement);
                    RealMatrix Q = buildProcessNoise(dt);
                    state.predict(dt, Q);

                    double rDist = (measurement.accuracyMetres() < ACCURACY_THRESHOLD_METRES)
                            ? R_DIST_GOOD_GPS : R_DIST_POOR_GPS;
                    double rSpeed = (measurement.accuracyMetres() < ACCURACY_THRESHOLD_METRES)
                            ? R_SPEED_GOOD_GPS : R_SPEED_POOR_GPS;

                    double measuredDist = state.distanceCoveredKm()
                            + measurement.speedKmh() * (dt / 3600.0);
                    state.update(measuredDist, measurement.speedKmh(), rDist, rSpeed);

                    EtaResult eta = state.computeEta(LAMBDA);

                    return stateRepository.save(state)
                            .then(eventPublisher.publishEtaUpdated(
                                    EtaUpdatedEvent.of(null, missionId,
                                            eta.expected(), eta.lowerBound(), eta.upperBound(),
                                            eta.confidenceLevel())))
                            .thenReturn(eta);
                });
    }

    @Override
    public Mono<EtaResult> computeInitialEta(String missionId, double totalDistanceKm,
                                               double estimatedSpeedKmh) {
        KalmanState state = KalmanState.initialize(missionId, totalDistanceKm);
        double[] sv = state.stateVector();
        sv[1] = estimatedSpeedKmh;
        state = KalmanState.rehydrate(missionId, sv, state.covarianceMatrix(),
                totalDistanceKm, state.lastUpdatedAt());

        EtaResult eta = state.computeEta(LAMBDA);
        return stateRepository.save(state).thenReturn(eta);
    }

    public Mono<EtaResult> computeInitialEtaLogNormal(String missionId,
                                                        double totalDistanceKm,
                                                        double[] arcDistances,
                                                        double[] arcMuLog,
                                                        double[] arcSigmaLog) {
        double expectedTime = 0;
        double varianceTime = 0;
        for (int i = 0; i < arcDistances.length; i++) {
            double mu = arcMuLog[i];
            double sigma2 = arcSigmaLog[i] * arcSigmaLog[i];
            expectedTime += Math.exp(mu + sigma2 / 2.0);
            varianceTime += (Math.exp(sigma2) - 1.0) * Math.exp(2 * mu + sigma2);
        }

        double sdTime = Math.sqrt(varianceTime);
        double etaHours = expectedTime + LAMBDA * sdTime;
        double etaMinHours = expectedTime - LAMBDA * sdTime;
        if (etaMinHours < 0) etaMinHours = expectedTime * 0.5;

        java.time.Instant now = java.time.Instant.now();
        long etaMs = (long) (etaHours * 3_600_000);
        long etaMinMs = (long) (etaMinHours * 3_600_000);
        long etaMaxMs = (long) ((expectedTime + 2 * LAMBDA * sdTime) * 3_600_000);

        EtaResult eta = new EtaResult(
                now.plusMillis(etaMs),
                now.plusMillis(etaMinMs),
                now.plusMillis(etaMaxMs),
                0.88,
                now
        );

        KalmanState state = KalmanState.initialize(missionId, totalDistanceKm);
        return stateRepository.save(state).thenReturn(eta);
    }

    private double computeDeltaTimeSeconds(KalmanState state, GPSMeasurement measurement) {
        Duration delta = Duration.between(state.lastUpdatedAt(), measurement.timestamp());
        double dt = delta.toMillis() / 1000.0;
        return Math.max(dt, 0.1);
    }

    private RealMatrix buildProcessNoise(double dt) {
        return MatrixUtils.createRealDiagonalMatrix(new double[]{
                Q_DISTANCE * dt * dt,
                Q_SPEED * dt,
                Q_BIAS
        });
    }
}
