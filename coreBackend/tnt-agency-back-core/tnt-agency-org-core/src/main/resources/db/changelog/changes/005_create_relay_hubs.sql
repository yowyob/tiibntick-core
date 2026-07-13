--liquibase formatted sql
--changeset jeff-belekotan:005_create_relay_hubs
--comment: Hub configuration only — parcel movements live in tnt-inventory-core

CREATE TABLE IF NOT EXISTS agency_org.agency_relay_hubs (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    agency_id             UUID NOT NULL REFERENCES agency_org.agencies(id) ON DELETE CASCADE,
    branch_id             UUID,
    name                  VARCHAR(255) NOT NULL,
    code                  VARCHAR(50) NOT NULL UNIQUE,
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    capacity_units        INTEGER NOT NULL,
    current_occupancy     INTEGER NOT NULL DEFAULT 0,
    retention_delay_hours INTEGER NOT NULL DEFAULT 72,
    opening_hours         VARCHAR(100),
    core_hub_id           UUID,
    addr_street           VARCHAR(255),
    addr_landmark         VARCHAR(255),
    addr_quarter          VARCHAR(100),
    addr_city             VARCHAR(100) NOT NULL,
    addr_region           VARCHAR(100),
    addr_country          VARCHAR(100) NOT NULL,
    addr_postal_code      VARCHAR(20),
    latitude              DOUBLE PRECISION,
    longitude             DOUBLE PRECISION,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agency_org_hubs_agency ON agency_org.agency_relay_hubs (agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_org_hubs_core ON agency_org.agency_relay_hubs (core_hub_id);

ALTER TABLE agency_org.agency_relay_hubs ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_org.agency_relay_hubs CASCADE;
