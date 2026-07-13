--liquibase formatted sql
--changeset jeff-belekotan:002_create_staff_members
--comment: Ported from tnt-agency V007__staff_members_schema (schema hr → agency_hr)

CREATE TABLE IF NOT EXISTS agency_hr.staff_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    agency_id       UUID NOT NULL,
    branch_id       UUID,
    full_name       VARCHAR(255) NOT NULL,
    phone           VARCHAR(30) NOT NULL,
    email           VARCHAR(255) NOT NULL DEFAULT '',
    role            VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    suspended_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agency_hr_staff_tenant ON agency_hr.staff_members (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agency_hr_staff_agency ON agency_hr.staff_members (agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_hr_staff_status ON agency_hr.staff_members (status);

ALTER TABLE agency_hr.staff_members ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_hr.staff_members CASCADE;
