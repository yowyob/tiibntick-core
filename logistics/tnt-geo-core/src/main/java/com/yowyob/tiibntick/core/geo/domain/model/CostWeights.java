package com.yowyob.tiibntick.core.geo.domain.model;

import java.util.Objects;

/**
 * Immutable set of weights for the 5-dimensional arc cost function:
 *   omega(a,t) = alpha*d_tilde + beta*T_tilde + gamma*rho_tilde + delta*xi_tilde + eta*c_fuel_tilde
 *
 * Weights must all be non-negative. The set is considered valid when the sum is positive.
 * Default calibration is provided via {@link #defaultWeights()}.
 *
 * Author: MANFOUO Braun
 */
public final class CostWeights {

    private final double alpha;
    private final double beta;
    private final double gamma;
    private final double delta;
    private final double eta;

    private CostWeights(double alpha, double beta, double gamma, double delta, double eta) {
        validate("alpha", alpha);
        validate("beta", beta);
        validate("gamma", gamma);
        validate("delta", delta);
        validate("eta", eta);
        if (alpha + beta + gamma + delta + eta == 0.0) {
            throw new IllegalArgumentException("At least one weight must be positive");
        }
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
        this.delta = delta;
        this.eta = eta;
    }

    public static CostWeights of(double alpha, double beta, double gamma, double delta, double eta) {
        return new CostWeights(alpha, beta, gamma, delta, eta);
    }

    /**
     * Default weights calibrated for TiiBnTick Cameroonian urban delivery context.
     * Priority: time > distance > cost > weather > penibility
     */
    public static CostWeights defaultWeights() {
        return new CostWeights(0.25, 0.35, 0.10, 0.15, 0.15);
    }

    /**
     * Weights favouring the shortest physical distance (e.g. motorcycle courier).
     */
    public static CostWeights distancePriority() {
        return new CostWeights(0.50, 0.20, 0.10, 0.10, 0.10);
    }

    /**
     * Weights favouring the fastest route (e.g. express SLA).
     */
    public static CostWeights timePriority() {
        return new CostWeights(0.15, 0.55, 0.10, 0.10, 0.10);
    }

    /**
     * Weights favouring the cheapest route (e.g. economy tier).
     */
    public static CostWeights economyPriority() {
        return new CostWeights(0.15, 0.15, 0.20, 0.15, 0.35);
    }

    public double alpha() { return alpha; }
    public double beta()  { return beta;  }
    public double gamma() { return gamma; }
    public double delta() { return delta; }
    public double eta()   { return eta;   }

    private static void validate(String name, double value) {
        if (value < 0.0) {
            throw new IllegalArgumentException("Weight " + name + " must be >= 0, got: " + value);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CostWeights that)) return false;
        return Double.compare(that.alpha, alpha) == 0
                && Double.compare(that.beta, beta) == 0
                && Double.compare(that.gamma, gamma) == 0
                && Double.compare(that.delta, delta) == 0
                && Double.compare(that.eta, eta) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(alpha, beta, gamma, delta, eta);
    }

    @Override
    public String toString() {
        return "CostWeights{alpha=" + alpha + ", beta=" + beta + ", gamma=" + gamma
                + ", delta=" + delta + ", eta=" + eta + "}";
    }
}
