package com.yowyob.tiibntick.core.gofreelancer.application.usecase;

/** Test-only window on the package-private TOPSIS weights. */
public final class TopsisWeightsProbe {
    private TopsisWeightsProbe() { }

    public static double sum() {
        return TopsisRankingUseCase.W_DISTANCE + TopsisRankingUseCase.W_RATING + TopsisRankingUseCase.W_CAPACITY
                + TopsisRankingUseCase.W_VEHICLE_TYPE + TopsisRankingUseCase.W_LOYALTY;
    }

    public static double loyalty() { return TopsisRankingUseCase.W_LOYALTY; }

    public static double rating() { return TopsisRankingUseCase.W_RATING; }
}
