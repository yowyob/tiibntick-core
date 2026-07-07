package com.yowyob.tiibntick.core.route.application.service;

import com.yowyob.tiibntick.core.route.domain.model.EtaResult;
import com.yowyob.tiibntick.core.route.domain.model.RoutePath;
import com.yowyob.tiibntick.core.route.domain.model.RouteSegment;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EtaComputeService {

    private static final double LAMBDA = 1.5;
    private static final double DEFAULT_SIGMA_RATIO = 0.25;

    public EtaResult computeInitial(RoutePath path, Instant departureTime) {
        double expectedHours = 0;
        double varianceHours = 0;

        for (RouteSegment seg : path.segments()) {
            double travelTimeH = seg.distanceKm() / Math.max(seg.partialCost(), 0.01);
            double mu = Math.log(travelTimeH) - 0.5 * DEFAULT_SIGMA_RATIO * DEFAULT_SIGMA_RATIO;
            double sigma2 = DEFAULT_SIGMA_RATIO * DEFAULT_SIGMA_RATIO;
            expectedHours += Math.exp(mu + sigma2 / 2.0);
            varianceHours += (Math.exp(sigma2) - 1.0) * Math.exp(2 * mu + sigma2);
        }

        if (path.segments().isEmpty()) {
            return new EtaResult(departureTime, departureTime, departureTime, 1.0, Instant.now());
        }

        double sdHours = Math.sqrt(varianceHours);
        long expectedMs = (long) (expectedHours * 3_600_000);
        long marginMs = (long) (LAMBDA * sdHours * 3_600_000);

        Instant expected = departureTime.plusMillis(expectedMs);
        Instant lower = departureTime.plusMillis(Math.max(expectedMs - marginMs, 0));
        Instant upper = departureTime.plusMillis(expectedMs + marginMs);

        return new EtaResult(expected, lower, upper, 0.88, Instant.now());
    }

    public EtaResult computeFromDistanceAndSpeed(double remainingKm, double avgSpeedKmh,
                                                   Instant now) {
        if (avgSpeedKmh <= 0) avgSpeedKmh = 5.0;
        double expectedH = remainingKm / avgSpeedKmh;
        double marginH = LAMBDA * DEFAULT_SIGMA_RATIO * expectedH;
        long expectedMs = (long) (expectedH * 3_600_000);
        long marginMs = (long) (marginH * 3_600_000);

        return new EtaResult(
                now.plusMillis(expectedMs),
                now.plusMillis(Math.max(expectedMs - marginMs, 0)),
                now.plusMillis(expectedMs + marginMs),
                0.80,
                now
        );
    }
}
