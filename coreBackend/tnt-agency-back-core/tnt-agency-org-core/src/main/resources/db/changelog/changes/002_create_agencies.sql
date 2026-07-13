--liquibase formatted sql
--changeset jeff-belekotan:002_create_agencies
--comment: Agency legal entities (migrated from tnt-agency schema agency.agencies)

CREATE TABLE IF NOT EXISTS agency_org.agencies (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    agency_code             VARCHAR(50) NOT NULL UNIQUE,
    type                    VARCHAR(30) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING_VALIDATION',
    registration_number     VARCHAR(100) NOT NULL,
    addr_street             VARCHAR(255),
    addr_landmark           VARCHAR(255),
    addr_quarter            VARCHAR(100),
    addr_city               VARCHAR(100) NOT NULL,
    addr_region             VARCHAR(100),
    addr_country            VARCHAR(100) NOT NULL,
    addr_postal_code        VARCHAR(20),
    addr_lat                DOUBLE PRECISION,
    addr_lon                DOUBLE PRECISION,
    contact_email           VARCHAR(255) NOT NULL,
    contact_phone           VARCHAR(20) NOT NULL,
    logo_url                VARCHAR(512),
    website                 VARCHAR(512),
    kernel_organization_id  UUID,
    kernel_business_actor_id UUID,
    core_agency_id          UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agency_org_agencies_tenant ON agency_org.agencies (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agency_org_agencies_status ON agency_org.agencies (status);
CREATE INDEX IF NOT EXISTS idx_agency_org_agencies_core_id ON agency_org.agencies (core_agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_org_agencies_kernel_org ON agency_org.agencies (kernel_organization_id);

ALTER TABLE agency_org.agencies ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_org.agencies CASCADE;
