package com.yowyob.tiibntick.core.route.domain.model;

public record CostParams(
        double alphaCoeff,
        double betaCoeff,
        double gammaCoeff,
        double deltaCoeff,
        double etaCoeff,
        double fuelPricePerLiterXAF,
        double vehicleFuelConsumptionL100km,
        double wearCostPerKmXAF,
        double driverTimeValuePerMinXAF
) {
    public CostParams {
        if (alphaCoeff < 0 || betaCoeff < 0 || gammaCoeff < 0 || deltaCoeff < 0 || etaCoeff < 0)
            throw new IllegalArgumentException("All coefficients must be >= 0");
    }

    public static CostParams defaults() {
        return new CostParams(0.25, 0.35, 0.10, 0.15, 0.15,
                800.0, 5.0, 15.0, 10.0);
    }

    public com.yowyob.tiibntick.core.geo.domain.model.CostWeights toCostWeights() {
        return com.yowyob.tiibntick.core.geo.domain.model.CostWeights.of(
                alphaCoeff, betaCoeff, gammaCoeff, deltaCoeff, etaCoeff);
    }
}
