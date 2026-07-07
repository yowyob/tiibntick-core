-- liquibase formatted sql
-- Author: MANFOUO Braun
-- Module: tnt-organization-core (L2 Identity)
-- Description: Creates the tnt_agency table — top-level TiiBnTick legal entity.
--
-- Kernel integration: organization_id references RT-comops-organization-core
-- (logical reference only — no physical FK to Kernel database).

-- changeset manfouo-braun:v0002-create-tnt-agency dbms:postgresql splitStatements:true endDelimiter:;
CREATE TABLE IF NOT EXISTS tnt_agency
(
    -- TiiBnTick internal primary key
    id                        UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- Kernel integration key: references RT-comops-organization-core Organization entity.
    -- NOT NULL: every TiiBnTick agency must map to a Kernel organization.
    -- No physical FK: integration is logical (validated via KernelOrganizationPort).
    organization_id           UUID         NOT NULL,

    -- Multi-tenant isolation
    tenant_id                 UUID         NOT NULL,

    -- Business fields
    name                      VARCHAR(255) NOT NULL,

    -- National commerce registry number (e.g., RCCM in Cameroon, CAC in Nigeria).
    commerce_registry_number  VARCHAR(100),

    -- Primary transaction currency (ISO 4217). Defaults to XAF (CFA Franc BEAC).
    primary_currency          VARCHAR(10)  NOT NULL DEFAULT 'XAF',

    -- Audit timestamps (UTC)
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tnt_agency PRIMARY KEY (id)
);

-- Index on organization_id for Kernel reference lookups
-- changeset manfouo-braun:v0002-idx-agency-organization-id dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_agency_organization_id
    ON tnt_agency (organization_id);

-- Index on tenant_id for multi-tenant queries
-- changeset manfouo-braun:v0002-idx-agency-tenant-id dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_agency_tenant_id
    ON tnt_agency (tenant_id);
