package com.yowyob.tiibntick.core.route.domain.model;

import org.apache.commons.math3.linear.*;

import java.time.Instant;
import java.util.Objects;

public final class KalmanState {

    private static final double ALPHA_B = 0.95;

    private final String missionId;
    private RealVector x;
    private RealMatrix P;
    private final double totalDistanceKm;
    private Instant lastUpdatedAt;

    private KalmanState(String missionId, RealVector x, RealMatrix P,
                        double totalDistanceKm, Instant lastUpdatedAt) {
        this.missionId = Objects.requireNonNull(missionId);
        this.x = x.copy();
        this.P = P.copy();
        this.totalDistanceKm = totalDistanceKm;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public static KalmanState initialize(String missionId, double totalDistanceKm) {
        RealVector x0 = new ArrayRealVector(new double[]{0.0, 0.0, 0.0});
        RealMatrix P0 = MatrixUtils.createRealDiagonalMatrix(new double[]{100.0, 25.0, 4.0});
        return new KalmanState(missionId, x0, P0, totalDistanceKm, Instant.now());
    }

    public static KalmanState rehydrate(String missionId, double[] stateVector,
                                         double[][] covMatrix, double totalDistanceKm,
                                         Instant lastUpdatedAt) {
        RealVector x = new ArrayRealVector(stateVector);
        RealMatrix P = new Array2DRowRealMatrix(covMatrix);
        return new KalmanState(missionId, x, P, totalDistanceKm, lastUpdatedAt);
    }

    public KalmanState predict(double deltaTimeSeconds, RealMatrix Q) {
        double dtHours = deltaTimeSeconds / 3600.0;
        RealMatrix F = new Array2DRowRealMatrix(new double[][]{
                {1.0, dtHours, 0.0},
                {0.0, 1.0,     0.0},
                {0.0, 0.0,     ALPHA_B}
        });
        this.x = F.operate(this.x);
        this.P = F.multiply(this.P).multiply(F.transpose()).add(Q);
        this.lastUpdatedAt = Instant.now();
        return this;
    }

    public KalmanState update(double distanceMeasuredKm, double speedMeasuredKmh,
                               double rDist, double rSpeed) {
        RealMatrix H = new Array2DRowRealMatrix(new double[][]{
                {1.0, 0.0, 0.0},
                {0.0, 1.0, 0.0}
        });
        RealVector z = new ArrayRealVector(new double[]{distanceMeasuredKm, speedMeasuredKmh});
        RealMatrix R = new Array2DRowRealMatrix(new double[][]{
                {rDist, 0.0},
                {0.0, rSpeed}
        });

        RealVector innovation = z.subtract(H.operate(this.x));
        RealMatrix S = H.multiply(this.P).multiply(H.transpose()).add(R);

        RealMatrix K;
        try {
            K = this.P.multiply(H.transpose())
                    .multiply(new LUDecomposition(S).getSolver().getInverse());
        } catch (SingularMatrixException e) {
            K = MatrixUtils.createRealMatrix(3, 2);
        }

        this.x = this.x.add(K.operate(innovation));
        RealMatrix I3 = MatrixUtils.createRealIdentityMatrix(3);
        this.P = I3.subtract(K.multiply(H)).multiply(this.P);
        this.lastUpdatedAt = Instant.now();
        return this;
    }

    public double distanceCoveredKm() { return x.getEntry(0); }
    public double speedKmh()          { return Math.max(x.getEntry(1), 0.0); }
    public double trafficBiasKmh()    { return x.getEntry(2); }
    public double remainingDistanceKm() { return Math.max(totalDistanceKm - distanceCoveredKm(), 0.0); }
    public double speedVariance()     { return P.getEntry(1, 1); }

    public EtaResult computeEta(double lambda) {
        double remaining = remainingDistanceKm();
        double speed = speedKmh();
        if (speed <= 0.5) speed = 5.0;

        double expectedTimeHours = remaining / speed;
        double marginHours = lambda * Math.sqrt(speedVariance()) * remaining / (speed * speed);

        Instant now = Instant.now();
        long expectedMs = (long) (expectedTimeHours * 3_600_000);
        long marginMs = (long) (marginHours * 3_600_000);

        Instant expected = now.plusMillis(expectedMs);
        Instant lower = now.plusMillis(Math.max(expectedMs - marginMs, 0));
        Instant upper = now.plusMillis(expectedMs + marginMs);

        return new EtaResult(expected, lower, upper, 0.80, now);
    }

    public String missionId()         { return missionId; }
    public double totalDistanceKm()   { return totalDistanceKm; }
    public Instant lastUpdatedAt()    { return lastUpdatedAt; }
    public double[] stateVector()     { return x.toArray(); }
    public double[][] covarianceMatrix() { return P.getData(); }
    public RealVector x()             { return x; }
    public RealMatrix P()             { return P; }
}
