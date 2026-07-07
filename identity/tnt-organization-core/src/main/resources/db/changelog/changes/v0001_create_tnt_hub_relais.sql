-- liquibase formatted sql
-- Author: MANFOUO Braun
-- Module: tnt-organization-core (L2 Identity)
-- Description: Creates the tnt_hub_relais table for geolocated relay hubs.
--
-- Kernel integration: organization_id references RT-comops-organization-core
-- (logical reference only — no physical FK to Kernel database).

-- changeset manfouo-braun:v0001-create-tnt-hub-relais dbms:postgresql splitStatements:true endDelimiter:;
CREATE TABLE IF NOT EXISTS tnt_hub_relais
(
    -- TiiBnTick internal primary key
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- Kernel integration key: references RT-comops-organization-core Organization entity.
    -- NOT NULL: every relay hub must be owned by a Kernel organization.
    -- No physical FK: integration is logical (validated at application layer via KernelOrganizationPort).
    organization_id      UUID         NOT NULL,

    -- Multi-tenant isolation
    tenant_id            UUID         NOT NULL,

    -- Business fields
    name                 VARCHAR(255) NOT NULL,
    max_parcel_capacity  INTEGER      NOT NULL CHECK (max_parcel_capacity > 0),

    -- PostGIS WKT POINT string (SRID 4326), e.g., POINT(9.7022 4.0511) for Douala.
    -- Used with ST_GeomFromText for spatial queries (ST_Within, ST_DWithin).
    geographic_point_wkt TEXT,

    opening_hours        VARCHAR(255),

    -- Operator reference: UUID of the managing actor in tnt-actor-core. Nullable.
    operator_id          UUID,

    -- Relay hub availability status
    operational          BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Audit timestamps (UTC)
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tnt_hub_relais PRIMARY KEY (id)
);

-- Index on organization_id for Kernel reference lookups
-- changeset manfouo-braun:v0001-idx-hub-organization-id dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_hub_relais_organization_id
    ON tnt_hub_relais (organization_id);

-- Index on tenant_id for multi-tenant queries
-- changeset manfouo-braun:v0001-idx-hub-tenant-id dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_hub_relais_tenant_id
    ON tnt_hub_relais (tenant_id);

-- Composite index supporting the most common query: operational hubs by organization
-- changeset manfouo-braun:v0001-idx-hub-org-operational dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_hub_relais_org_operational
    ON tnt_hub_relais (organization_id, operational);
