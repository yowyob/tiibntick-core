package com.yowyob.tiibntick.core.billing.cost.domain.enums;

/**
 * Road surface type affecting vehicle wear.
 * Maps to ρ (penibility index) in the mathematical model:
 *   c_wear(a) = dist × wearPerKm × ρ(a) × δ(weather)
 *
 * @author MANFOUO Braun
 */
public enum RoadType {
    /** Paved highway — minimal wear. ρ = 1.0 */
    HIGHWAY(1.0, 0.0),
    /** Standard urban paved road. ρ = 1.2 */
    URBAN_PAVED(1.2, 0.1),
    /** Degraded or potholed road — common in Yaoundé/Douala. ρ = 1.8 */
    DEGRADED(1.8, 0.4),
    /** Unpaved dirt road — high wear. ρ = 2.5 */
    DIRT(2.5, 0.7),
    /** Off-road / bush track. ρ = 3.5 */
    OFF_ROAD(3.5, 1.0),
    /** Unknown road type — defaults to urban paved. */
    UNKNOWN(1.2, 0.1);

    /** ρ — road degradation factor multiplying vehicle wear. */
    private final double degradationFactor;
    /** Penibility index for this road type (in [0,1]). */
    private final double penibilityIndex;

    RoadType(double degradationFactor, double penibilityIndex) {
        this.degradationFactor = degradationFactor;
        this.penibilityIndex = penibilityIndex;
    }

    public double degradationFactor() {
        return degradationFactor;
    }

    public double penibilityIndex() {
        return penibilityIndex;
    }
}
