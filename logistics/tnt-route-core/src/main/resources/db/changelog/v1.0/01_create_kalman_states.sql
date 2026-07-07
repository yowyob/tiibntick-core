-- tnt-route-core: kalman_states table
-- Author: MANFOUO Braun
CREATE SCHEMA IF NOT EXISTS tnt_route;

CREATE TABLE IF NOT EXISTS tnt_route.kalman_states (
    mission_id        VARCHAR(36)      NOT NULL,
    state_s           DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    state_v           DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    state_b           DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    cov_matrix_json   TEXT             NOT NULL,
    total_distance_km DOUBLE PRECISION NOT NULL,
    last_updated_at   TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT kalman_states_pk PRIMARY KEY (mission_id)
);

CREATE INDEX IF NOT EXISTS idx_kalman_mission ON tnt_route.kalman_states (mission_id);
