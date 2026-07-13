--liquibase formatted sql
--changeset jeff-belekotan:004_create_contracts

CREATE TABLE IF NOT EXISTS agency_hr.contracts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    agency_id           UUID NOT NULL,
    deliverer_id        UUID NOT NULL REFERENCES agency_hr.deliverers(id) ON DELETE CASCADE,
    contract_type       VARCHAR(20) NOT NULL,
    start_date          DATE NOT NULL,
    end_date            DATE,
    remuneration_model  VARCHAR(30) NOT NULL,
    base_salary         NUMERIC(14,2),
    commission_rate     NUMERIC(5,4),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    signed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agency_hr_contracts_tenant ON agency_hr.contracts (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agency_hr_contracts_deliverer ON agency_hr.contracts (deliverer_id);
CREATE INDEX IF NOT EXISTS idx_agency_hr_contracts_status ON agency_hr.contracts (status);

ALTER TABLE agency_hr.contracts ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_hr.contracts CASCADE;
