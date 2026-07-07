package com.yowyob.tiibntick.core.route.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.route.domain.model.KalmanState;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table(schema = "tnt_route", value = "kalman_states")
public class KalmanStateEntity {

    @Id
    @Column("mission_id")
    private String missionId;

    @Column("state_s")
    private double stateS;

    @Column("state_v")
    private double stateV;

    @Column("state_b")
    private double stateB;

    @Column("cov_matrix_json")
    private String covMatrixJson;

    @Column("total_distance_km")
    private double totalDistanceKm;

    @Column("last_updated_at")
    private Instant lastUpdatedAt;

    public KalmanStateEntity() {}

    public static KalmanStateEntity fromDomain(KalmanState ks) {
        KalmanStateEntity e = new KalmanStateEntity();
        e.missionId = ks.missionId();
        double[] sv = ks.stateVector();
        e.stateS = sv[0];
        e.stateV = sv[1];
        e.stateB = sv[2];
        e.totalDistanceKm = ks.totalDistanceKm();
        e.lastUpdatedAt = ks.lastUpdatedAt();
        double[][] cov = ks.covarianceMatrix();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (sb.length() > 0) sb.append(',');
                sb.append(cov[i][j]);
            }
        }
        e.covMatrixJson = sb.toString();
        return e;
    }

    public KalmanState toDomain() {
        double[] sv = {stateS, stateV, stateB};
        String[] parts = covMatrixJson.split(",");
        double[][] cov = new double[3][3];
        int idx = 0;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                cov[i][j] = Double.parseDouble(parts[idx++].trim());
        return KalmanState.rehydrate(missionId, sv, cov, totalDistanceKm, lastUpdatedAt);
    }

    public String getMissionId() { return missionId; }
    public void setMissionId(String m) { this.missionId = m; }
    public double getStateS() { return stateS; }
    public void setStateS(double s) { this.stateS = s; }
    public double getStateV() { return stateV; }
    public void setStateV(double v) { this.stateV = v; }
    public double getStateB() { return stateB; }
    public void setStateB(double b) { this.stateB = b; }
    public String getCovMatrixJson() { return covMatrixJson; }
    public void setCovMatrixJson(String c) { this.covMatrixJson = c; }
    public double getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(double d) { this.totalDistanceKm = d; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(Instant t) { this.lastUpdatedAt = t; }
}
