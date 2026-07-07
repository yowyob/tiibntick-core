package com.yowyob.tiibntick.core.delivery.adapter.out.messaging;

import com.yowyob.tiibntick.core.delivery.application.port.out.EtaComputationPort;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * In-process ETA computation adapter.
 *
 * <p>Initial ETA: log-normal model based on average urban speed in Yaoundé (25 km/h).
 *
 * <p>Kalman filter refinement: one-dimensional Kalman update on remaining time.
 * <pre>
 *   K = P_prior / (P_prior + R)     // Kalman gain
 *   x_post = x_prior + K * (z - x_prior)  // updated estimate
 *   P_post = (1 - K) * P_prior      // updated covariance
 * </pre>
 * where R = measurement noise (GPS accuracy + traffic variability),
 * process noise Q = 0.05 (slowly evolving variable).
 *
 * @author MANFOUO Braun
 */
@Component
public class SimpleEtaAdapter implements EtaComputationPort {

    /** Average urban delivery speed (km/h) — Yaoundé-calibrated. */
    private static final double AVG_SPEED_KMH = 25.0;

    /** Kalman measurement noise (minutes²) — GPS + traffic variability. */
    private static final double R_NOISE = 25.0;

    /** Kalman process noise (minutes²) — slow drift in travel conditions. */
    private static final double Q_PROCESS = 0.05;

    /** Initial state covariance (minutes²). */
    private static final double P_INITIAL = 100.0;

    @Override
    public Mono<EtaEstimate> computeInitial(GeoCoordinates origin,
                                             GeoCoordinates destination,
                                             double distanceKm) {
        int estimatedMinutes = (int) Math.ceil((distanceKm / AVG_SPEED_KMH) * 60);
        Instant arrival = Instant.now().plusSeconds(estimatedMinutes * 60L);
        return Mono.just(EtaEstimate.of(arrival, distanceKm, estimatedMinutes));
    }

    @Override
    public Mono<EtaEstimate> refineWithKalman(GeoCoordinates currentPosition,
                                               GeoCoordinates destination,
                                               EtaEstimate previousEta) {
        double remainingKm = currentPosition.haversineDistanceTo(destination);
        double measuredMinutes = (remainingKm / AVG_SPEED_KMH) * 60.0;

        // Extended Kalman filter — scalar case
        double xPrior = previousEta.remainingMinutes();
        double pPrior = P_INITIAL;

        // Predict
        double xPred = xPrior;
        double pPred = pPrior + Q_PROCESS;

        // Update
        double k     = pPred / (pPred + R_NOISE);
        double xPost = xPred + k * (measuredMinutes - xPred);
        double pPost = (1 - k) * pPred;

        int refinedMinutes = (int) Math.max(1, Math.round(xPost));
        Instant refinedArrival = Instant.now().plusSeconds(refinedMinutes * 60L);

        // Confidence increases as variance decreases
        double confidence = Math.min(0.98, 0.80 + (1.0 - pPost / P_INITIAL) * 0.18);

        long slack = Math.max(refinedMinutes / 10L, 3L) * 60L;
        return Mono.just(new EtaEstimate(
                refinedArrival,
                refinedArrival.minusSeconds(slack),
                refinedArrival.plusSeconds(slack),
                confidence,
                remainingKm,
                refinedMinutes));
    }
}
