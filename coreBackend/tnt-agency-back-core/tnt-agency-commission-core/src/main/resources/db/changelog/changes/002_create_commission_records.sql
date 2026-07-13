--liquibase formatted sql
--changeset jeff-belekotan:002_create_commission_records
--comment: Ported from tnt-agency V002-05 (hr → agency_commercial)

CREATE TABLE IF NOT EXISTS agency_commercial.commission_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    agency_id       UUID NOT NULL,
    deliverer_id    UUID NOT NULL REFERENCES agency_hr.deliverers(id) ON DELETE CASCADE,
    mission_id      UUID,
    amount          NUMERIC(14,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'XAF',
    status          VARCHAR(20) NOT NULL DEFAULT 'CALCULATED',
    dispute_reason  TEXT,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agency_commercial_commission_tenant
    ON agency_commercial.commission_records (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agency_commercial_commission_deliverer
    ON agency_commercial.commission_records (deliverer_id);
CREATE INDEX IF NOT EXISTS idx_agency_commercial_commission_agency
    ON agency_commercial.commission_records (agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_commercial_commission_status
    ON agency_commercial.commission_records (status);

ALTER TABLE agency_commercial.commission_records ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_commercial.commission_records CASCADE;
