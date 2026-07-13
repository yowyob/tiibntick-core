package com.yowyob.tiibntick.core.marketback.application.port.in.result;

import java.util.UUID;

/**
 * Response DTO — result of a price simulation.
 * @author MANFOUO Braun
 */
public record PriceSimulationResponse(
        UUID offerId,
        String offerName,
        long estimatedPriceXaf,
        long basePriceXaf,
        long distanceFeeXaf,
        long weightFeeXaf,
        long urgencyFeeXaf,
        double distanceKm,
        String pickupCity,
        String deliveryCity,
        String etaEstimate
) {}
