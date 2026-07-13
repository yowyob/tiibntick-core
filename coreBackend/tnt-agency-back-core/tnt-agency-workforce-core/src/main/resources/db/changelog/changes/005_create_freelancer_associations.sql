--liquibase formatted sql
--changeset jeff-belekotan:005_create_freelancer_associations

CREATE TABLE IF NOT EXISTS agency_hr.freelancer_associations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    agency_id             UUID NOT NULL,
    freelancer_actor_id   UUID NOT NULL,
    commission_rate       NUMERIC(5,4) NOT NULL,
    start_date            DATE NOT NULL,
    end_date              DATE,
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    associated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agency_hr_freelancer_tenant ON agency_hr.freelancer_associations (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agency_hr_freelancer_agency ON agency_hr.freelancer_associations (agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_hr_freelancer_status ON agency_hr.freelancer_associations (status);

ALTER TABLE agency_hr.freelancer_associations ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_hr.freelancer_associations CASCADE;
