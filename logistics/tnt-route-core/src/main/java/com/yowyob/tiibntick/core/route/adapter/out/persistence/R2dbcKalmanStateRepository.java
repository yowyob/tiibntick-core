package com.yowyob.tiibntick.core.route.adapter.out.persistence;

import com.yowyob.tiibntick.core.route.adapter.out.persistence.entity.KalmanStateEntity;
import com.yowyob.tiibntick.core.route.application.port.out.IKalmanStateRepository;
import com.yowyob.tiibntick.core.route.domain.model.KalmanState;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public class R2dbcKalmanStateRepository implements IKalmanStateRepository {

    private final DatabaseClient db;

    public R2dbcKalmanStateRepository(DatabaseClient db) { this.db = db; }

    @Override
    public Mono<KalmanState> save(KalmanState state) {
        KalmanStateEntity e = KalmanStateEntity.fromDomain(state);
        String sql = """
                INSERT INTO tnt_route.kalman_states
                  (mission_id, state_s, state_v, state_b, cov_matrix_json,
                   total_distance_km, last_updated_at)
                VALUES (:mid, :s, :v, :b, :cov, :dist, :upd)
                ON CONFLICT (mission_id) DO UPDATE
                  SET state_s = EXCLUDED.state_s,
                      state_v = EXCLUDED.state_v,
                      state_b = EXCLUDED.state_b,
                      cov_matrix_json = EXCLUDED.cov_matrix_json,
                      last_updated_at = EXCLUDED.last_updated_at
                """;
        return db.sql(sql)
                .bind("mid", e.getMissionId())
                .bind("s", e.getStateS())
                .bind("v", e.getStateV())
                .bind("b", e.getStateB())
                .bind("cov", e.getCovMatrixJson())
                .bind("dist", e.getTotalDistanceKm())
                .bind("upd", e.getLastUpdatedAt())
                .fetch().rowsUpdated()
                .thenReturn(state);
    }

    @Override
    public Mono<KalmanState> findByMissionId(String missionId) {
        String sql = """
                SELECT mission_id, state_s, state_v, state_b,
                       cov_matrix_json, total_distance_km, last_updated_at
                FROM tnt_route.kalman_states WHERE mission_id = :mid
                """;
        return db.sql(sql).bind("mid", missionId)
                .map(row -> {
                    KalmanStateEntity e = new KalmanStateEntity();
                    e.setMissionId(row.get("mission_id", String.class));
                    e.setStateS(row.get("state_s", Double.class));
                    e.setStateV(row.get("state_v", Double.class));
                    e.setStateB(row.get("state_b", Double.class));
                    e.setCovMatrixJson(row.get("cov_matrix_json", String.class));
                    e.setTotalDistanceKm(row.get("total_distance_km", Double.class));
                    e.setLastUpdatedAt(row.get("last_updated_at", Instant.class));
                    return e;
                }).one().map(KalmanStateEntity::toDomain);
    }

    @Override
    public Mono<Void> deleteByMissionId(String missionId) {
        return db.sql("DELETE FROM tnt_route.kalman_states WHERE mission_id = :mid")
                .bind("mid", missionId)
                .fetch().rowsUpdated().then();
    }
}
