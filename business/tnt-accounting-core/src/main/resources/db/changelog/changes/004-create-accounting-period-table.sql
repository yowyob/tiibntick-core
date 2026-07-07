-- liquibase formatted sql
-- changeset manfouo-braun:004-accounting-period-table
-- comment: Monthly accounting periods controlling entry posting windows
CREATE TABLE IF NOT EXISTS accounting.accounting_periods (
    id        UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID        NOT NULL,
    year      INTEGER     NOT NULL,
    month     INTEGER     NOT NULL CHECK (month BETWEEN 1 AND 12),
    status    VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_accounting_periods_tenant_ym UNIQUE (tenant_id, year, month)
);
CREATE INDEX IF NOT EXISTS idx_ap_tenant_status ON accounting.accounting_periods (tenant_id, status);
-- rollback DROP TABLE IF EXISTS accounting.accounting_periods;
