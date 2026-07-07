package com.yowyob.tiibntick.core.geo.domain.model;

/**
 * Classification of road surface types used in the penibility component (gamma*rho)
 * of the 5D cost function omega(a,t).
 *
 * Penibility values are domain-calibrated for West African road conditions:
 * HIGHWAY    → 0.0  (smooth, fast)
 * PAVED      → 0.0  (urban/inter-city tarmac)
 * DEGRADED   → 0.5  (potholed tarmac, partial laterite)
 * DIRT       → 1.0  (laterite track, rainy-season risk)
 *
 * Author: MANFOUO Braun
 */
public enum RoadType {

    HIGHWAY(0.0),
    PAVED(0.0),
    DEGRADED(0.5),
    DIRT(1.0);

    private final double penibility;

    RoadType(double penibility) {
        this.penibility = penibility;
    }

    public double penibility() {
        return penibility;
    }
}
