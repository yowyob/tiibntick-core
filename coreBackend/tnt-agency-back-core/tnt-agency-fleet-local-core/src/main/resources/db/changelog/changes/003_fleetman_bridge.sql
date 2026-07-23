--liquibase formatted sql
--changeset manfouo-braun:003_fleetman_bridge
--comment: FleetMan bridge — link table + vehicle provenance columns

ALTER TABLE agency_fleet.vehicles
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'AGENCY',
    ADD COLUMN IF NOT EXISTS fleetman_vehicle_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_fleet_vehicles_fleetman_id
    ON agency_fleet.vehicles (fleetman_vehicle_id)
    WHERE fleetman_vehicle_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS agency_fleet.fleetman_link (
    agency_id           UUID PRIMARY KEY REFERENCES agency_org.agencies(id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL,
    fleetman_user_id    VARCHAR(64),
    fleetman_fleet_id   VARCHAR(64) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    refresh_token_enc   TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_fleetman_link_tenant ON agency_fleet.fleetman_link (tenant_id);

ALTER TABLE agency_fleet.fleetman_link ENABLE ROW LEVEL SECURITY;

--rollback ALTER TABLE agency_fleet.vehicles DROP COLUMN IF EXISTS source; ALTER TABLE agency_fleet.vehicles DROP COLUMN IF EXISTS fleetman_vehicle_id; ALTER TABLE agency_fleet.vehicles DROP COLUMN IF EXISTS last_synced_at; DROP TABLE IF EXISTS agency_fleet.fleetman_link CASCADE;
