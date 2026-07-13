--liquibase formatted sql
--changeset jeff-belekotan:002_create_vehicles
--comment: Ported from tnt-agency V003 + V015 core_vehicle_id

CREATE TABLE IF NOT EXISTS agency_fleet.vehicles (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    agency_id               UUID NOT NULL REFERENCES agency_org.agencies(id) ON DELETE CASCADE,
    branch_id               UUID,
    assigned_deliverer_id   UUID,
    license_plate           VARCHAR(20) NOT NULL,
    brand                   VARCHAR(100) NOT NULL,
    model                   VARCHAR(100) NOT NULL,
    year                    INTEGER NOT NULL,
    vehicle_type            VARCHAR(30) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    assigned_at             TIMESTAMPTZ,
    maintenance_started_at  TIMESTAMPTZ,
    core_vehicle_id         UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_vehicles_plate_tenant UNIQUE (license_plate, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_fleet_vehicles_tenant ON agency_fleet.vehicles (tenant_id);
CREATE INDEX IF NOT EXISTS idx_fleet_vehicles_agency ON agency_fleet.vehicles (agency_id);
CREATE INDEX IF NOT EXISTS idx_fleet_vehicles_status ON agency_fleet.vehicles (status);
CREATE INDEX IF NOT EXISTS idx_fleet_vehicles_core_vehicle_id ON agency_fleet.vehicles (core_vehicle_id);

ALTER TABLE agency_fleet.vehicles ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_fleet.vehicles CASCADE;
