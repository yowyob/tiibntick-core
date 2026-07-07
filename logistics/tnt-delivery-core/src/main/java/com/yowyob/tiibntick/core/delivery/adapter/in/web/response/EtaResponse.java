package com.yowyob.tiibntick.core.delivery.adapter.in.web.response;

import java.time.Instant;

/**
 * HTTP response for an ETA estimate.
 *
 * @author MANFOUO Braun
 */
public record EtaResponse(
        Instant estimatedArrival,
        Instant lowerBound,
        Instant upperBound,
        double confidenceScore,
        double remainingDistanceKm,
        int remainingMinutes,
        boolean kalmanRefined
) {}
