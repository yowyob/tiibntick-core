--liquibase formatted sql
--changeset jeff-belekotan:003_create_deliverers
--comment: Ported from tnt-agency V002-02 + V008 phone column

CREATE TABLE IF NOT EXISTS agency_hr.deliverers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    agency_id       UUID NOT NULL,
    branch_id       UUID,
    actor_id        UUID NOT NULL,
    phone           VARCHAR(30),
    status          VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    suspended_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agency_hr_deliverers_tenant ON agency_hr.deliverers (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agency_hr_deliverers_agency ON agency_hr.deliverers (agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_hr_deliverers_status ON agency_hr.deliverers (status);
CREATE INDEX IF NOT EXISTS idx_agency_hr_deliverers_phone ON agency_hr.deliverers (phone);

ALTER TABLE agency_hr.deliverers ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_hr.deliverers CASCADE;
