-- liquibase formatted sql
-- Author: MANFOUO Braun
-- Module: tnt-organization-core (L2 Identity)
-- Description: Creates the tnt_branch table — sub-office of a TiiBnTick Agency.
--
-- Kernel integration: organization_id references RT-comops-organization-core
-- (logical reference only — no physical FK to Kernel database).

-- changeset manfouo-braun:v0003-create-tnt-branch dbms:postgresql splitStatements:true endDelimiter:;
CREATE TABLE IF NOT EXISTS tnt_branch
(
    -- TiiBnTick internal primary key
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- Kernel integration key: references RT-comops-organization-core Organization entity.
    -- NOT NULL: every branch must be associated with a Kernel organization.
    -- No physical FK: integration is logical.
    organization_id     UUID         NOT NULL,

    -- Parent agency (physical FK within the TiiBnTick schema)
    agency_id           UUID         NOT NULL,

    -- Multi-tenant isolation
    tenant_id           UUID         NOT NULL,

    -- Business fields
    name                VARCHAR(255) NOT NULL,

    -- Physical address — free-form text to accommodate informal addressing systems
    -- common in Cameroon and other African contexts (e.g., "Face Marché Central, Akwa").
    address             TEXT,

    -- ServiceZone VO denormalized into two columns:
    -- Zone name (e.g., "Akwa Downtown Coverage Area")
    service_zone_name   VARCHAR(255),
    -- WKT POLYGON for the coverage area (SRID 4326). Nullable when no zone is assigned.
    service_zone_wkt    TEXT,

    -- Operational status
    active              BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Audit timestamps (UTC)
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tnt_branch PRIMARY KEY (id),

    -- Physical FK to tnt_agency within the same TiiBnTick schema
    CONSTRAINT fk_tnt_branch_agency
        FOREIGN KEY (agency_id) REFERENCES tnt_agency (id) ON DELETE RESTRICT
);

-- Index on organization_id for Kernel reference lookups
-- changeset manfouo-braun:v0003-idx-branch-organization-id dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_branch_organization_id
    ON tnt_branch (organization_id);

-- Index on agency_id for parent-child queries
-- changeset manfouo-braun:v0003-idx-branch-agency-id dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_branch_agency_id
    ON tnt_branch (agency_id);

-- Index on tenant_id for multi-tenant queries
-- changeset manfouo-braun:v0003-idx-branch-tenant-id dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_branch_tenant_id
    ON tnt_branch (tenant_id);

-- Composite index supporting the most common query: active branches per tenant
-- changeset manfouo-braun:v0003-idx-branch-tenant-active dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_branch_tenant_active
    ON tnt_branch (tenant_id, active);
