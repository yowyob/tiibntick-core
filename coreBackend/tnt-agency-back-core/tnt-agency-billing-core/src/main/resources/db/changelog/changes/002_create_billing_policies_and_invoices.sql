--liquibase formatted sql
--changeset jeff-belekotan:002_create_billing_policies_and_invoices
--comment: Agency billing policies and invoices (ported from BFF entities)

CREATE TABLE IF NOT EXISTS agency_commercial.billing_policies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    agency_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    currency        VARCHAR(3) NOT NULL DEFAULT 'XAF',
    base_price      NUMERIC(14,2) NOT NULL DEFAULT 0,
    price_per_km    NUMERIC(14,2) NOT NULL DEFAULT 0,
    price_per_kg    NUMERIC(14,2) NOT NULL DEFAULT 0,
    min_price       NUMERIC(14,2) NOT NULL DEFAULT 0,
    core_policy_id  UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agency_commercial_billing_policy_tenant
    ON agency_commercial.billing_policies (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agency_commercial_billing_policy_agency
    ON agency_commercial.billing_policies (agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_commercial_billing_policy_status
    ON agency_commercial.billing_policies (status);

CREATE TABLE IF NOT EXISTS agency_commercial.invoice_records (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    agency_id        UUID NOT NULL,
    mission_id       UUID NOT NULL,
    reference        VARCHAR(120) NOT NULL,
    amount           NUMERIC(14,2) NOT NULL,
    currency         VARCHAR(3) NOT NULL DEFAULT 'XAF',
    status           VARCHAR(20) NOT NULL DEFAULT 'GENERATED',
    core_invoice_id  UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agency_commercial_invoice_tenant
    ON agency_commercial.invoice_records (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agency_commercial_invoice_agency
    ON agency_commercial.invoice_records (agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_commercial_invoice_mission
    ON agency_commercial.invoice_records (mission_id);
CREATE INDEX IF NOT EXISTS idx_agency_commercial_invoice_status
    ON agency_commercial.invoice_records (status);

ALTER TABLE agency_commercial.billing_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE agency_commercial.invoice_records ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_commercial.invoice_records CASCADE;
--rollback DROP TABLE IF EXISTS agency_commercial.billing_policies CASCADE;
