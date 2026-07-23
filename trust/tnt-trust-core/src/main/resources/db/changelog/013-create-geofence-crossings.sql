-- ============================================================
-- Changeset 013: Geofence Crossings cache table
-- Author: MANFOUO Braun | Module: tnt-trust
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:013-create-geofence-crossings

CREATE TABLE IF NOT EXISTS tnt_trust.geofence_crossings (
    crossing_id          VARCHAR(36)      NOT NULL,
    actor_id             VARCHAR(36)      NOT NULL,
    tenant_id            VARCHAR(36)      NOT NULL,
    zone_id              VARCHAR(36)      NOT NULL,
    zone_name            VARCHAR(255)     NULL,
    zone_type            VARCHAR(100)     NULL,
    direction            VARCHAR(10)      NOT NULL,
    gps_lat              DOUBLE PRECISION NOT NULL,
    gps_lng              DOUBLE PRECISION NOT NULL,
    mission_id           VARCHAR(36)      NULL,
    occurred_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    blockchain_tx_hash   VARCHAR(66)      NULL,

    CONSTRAINT pk_geofence_crossings PRIMARY KEY (crossing_id)
);

COMMENT ON TABLE tnt_trust.geofence_crossings IS
    'Local cache of on-chain geofence zone crossing events. '
    'Populated when yow.trust.events.committed confirms GEOFENCE_CROSSING_RECORDED events.';

-- rollback DROP TABLE IF EXISTS tnt_trust.geofence_crossings;
