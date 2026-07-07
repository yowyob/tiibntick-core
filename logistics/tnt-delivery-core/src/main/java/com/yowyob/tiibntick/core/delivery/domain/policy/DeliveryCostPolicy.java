package com.yowyob.tiibntick.core.delivery.domain.policy;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsType;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryCost;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Domain policy encapsulating the multi-criteria delivery cost computation formula.
 *
 * <p>Based on the mathematical model (ColisCh@in / tiibntick thesis):
 * <pre>
 *   C_total = α·C_distance + β·C_time + γ·C_road + δ·C_weather + η·C_fuel
 * </pre>
 *
 * <p>Default Yaoundé-calibrated coefficients:
 * <ul>
 *   <li>α = 0.35 (distance is dominant factor)</li>
 *   <li>β = 0.25 (time)</li>
 *   <li>γ = 0.20 (road penibility — critical for Cameroonian context)</li>
 *   <li>δ = 0.10 (weather risk)</li>
 *   <li>η = 0.10 (fuel)</li>
 * </ul>
 *
 * Base rate: 500 XAF/km for motorbike (most common in Yaoundé informal logistics).
 *
 * @author MANFOUO Braun
 */
public final class DeliveryCostPolicy {

    // ── Composite cost weights (calibrated for Yaoundé/Cameroon) ─────
    private static final double ALPHA = 0.35;  // distance
    private static final double BETA  = 0.25;  // time
    private static final double GAMMA = 0.20;  // road penibility
    private static final double DELTA = 0.10;  // weather risk
    private static final double ETA   = 0.10;  // fuel

    // ── Base rate per km per vehicle type (XAF) ───────────────────────
    private static final double BIKE_RATE_XAF_KM       =  350.0;
    private static final double MOTORBIKE_RATE_XAF_KM  =  500.0;
    private static final double CAR_RATE_XAF_KM        =  800.0;
    private static final double VAN_RATE_XAF_KM        = 1200.0;
    private static final double TRUCK_RATE_XAF_KM      = 2000.0;

    // ── Urgency multipliers ───────────────────────────────────────────
    private static final double STANDARD_MULTIPLIER  = 1.00;
    private static final double EXPRESS_MULTIPLIER   = 1.40;
    private static final double SAME_DAY_MULTIPLIER  = 1.80;
    private static final double SCHEDULED_MULTIPLIER = 1.10;

    // ── Road penibility factor (default: moderate degraded road) ──────
    private static final double DEFAULT_ROAD_PENIBILITY = 0.25;

    // ── Minimum fare ─────────────────────────────────────────────────
    private static final BigDecimal MIN_FARE_XAF = BigDecimal.valueOf(500);

    private DeliveryCostPolicy() {}

    /**
     * Computes the delivery cost decomposed into the five model dimensions.
     *
     * @param distanceKm      Haversine or routed distance in km
     * @param estimatedMinutes expected transit time in minutes
     * @param roadPenibility  road quality factor in [0,1] (0=perfect, 1=impassable)
     * @param weatherRisk     weather degradation factor in [0,1]
     * @param logisticsType   vehicle type
     * @param urgency         delivery urgency level
     * @return detailed cost breakdown in XAF
     */
    public static DeliveryCost compute(double distanceKm,
                                        int estimatedMinutes,
                                        double roadPenibility,
                                        double weatherRisk,
                                        LogisticsType logisticsType,
                                        DeliveryUrgency urgency) {
        double baseRate    = baseRateFor(logisticsType);
        double urgencyMult = urgencyMultiplier(urgency);

        // Individual cost components (XAF)
        double distCost    = ALPHA * baseRate * distanceKm;
        double timeCost    = BETA  * baseRate * (estimatedMinutes / 60.0);
        double roadCost    = GAMMA * baseRate * distanceKm * roadPenibility;
        double weatherCost = DELTA * baseRate * distanceKm * weatherRisk;
        double fuelCost    = ETA   * baseRate * distanceKm;

        BigDecimal dist = bd(distCost * urgencyMult);
        BigDecimal time = bd(timeCost * urgencyMult);
        BigDecimal road = bd(roadCost * urgencyMult);
        BigDecimal weat = bd(weatherCost * urgencyMult);
        BigDecimal fuel = bd(fuelCost * urgencyMult);

        DeliveryCost cost = DeliveryCost.ofXaf(dist, time, road, weat, fuel);

        // Apply minimum fare
        if (cost.total().compareTo(MIN_FARE_XAF) < 0) {
            BigDecimal diff = MIN_FARE_XAF.subtract(cost.total());
            return DeliveryCost.ofXaf(dist.add(diff), time, road, weat, fuel);
        }
        return cost;
    }

    /**
     * Simplified computation using default road penibility and zero weather risk.
     */
    public static DeliveryCost computeSimple(double distanceKm,
                                              int estimatedMinutes,
                                              LogisticsType logisticsType,
                                              DeliveryUrgency urgency) {
        return compute(distanceKm, estimatedMinutes, DEFAULT_ROAD_PENIBILITY,
                0.0, logisticsType, urgency);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static double baseRateFor(LogisticsType type) {
        if (type == null) return MOTORBIKE_RATE_XAF_KM;
        return switch (type) {
            case BIKE      -> BIKE_RATE_XAF_KM;
            case MOTORBIKE -> MOTORBIKE_RATE_XAF_KM;
            case CAR       -> CAR_RATE_XAF_KM;
            case VAN       -> VAN_RATE_XAF_KM;
            case TRUCK     -> TRUCK_RATE_XAF_KM;
        };
    }

    private static double urgencyMultiplier(DeliveryUrgency urgency) {
        if (urgency == null) return STANDARD_MULTIPLIER;
        return switch (urgency) {
            case STANDARD  -> STANDARD_MULTIPLIER;
            case EXPRESS   -> EXPRESS_MULTIPLIER;
            case SAME_DAY  -> SAME_DAY_MULTIPLIER;
            case SCHEDULED -> SCHEDULED_MULTIPLIER;
        };
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
