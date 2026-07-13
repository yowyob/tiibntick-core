--liquibase formatted sql
--changeset jeff-belekotan:004_create_agency_branches

CREATE TABLE IF NOT EXISTS agency_org.agency_branches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    agency_id       UUID NOT NULL REFERENCES agency_org.agencies(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    code            VARCHAR(50) NOT NULL UNIQUE,
    manager_id      UUID,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    core_branch_id  UUID,
    addr_street     VARCHAR(255),
    addr_landmark   VARCHAR(255),
    addr_quarter    VARCHAR(100),
    addr_city       VARCHAR(100) NOT NULL,
    addr_region     VARCHAR(100),
    addr_country    VARCHAR(100) NOT NULL,
    addr_postal_code VARCHAR(20),
    addr_lat        DOUBLE PRECISION,
    addr_lon        DOUBLE PRECISION,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agency_org_branches_agency ON agency_org.agency_branches (agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_org_branches_core ON agency_org.agency_branches (core_branch_id);

ALTER TABLE agency_org.agency_branches ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_org.agency_branches CASCADE;
