-- liquibase formatted sql
-- changeset manfouo-braun:002-accounts-table
-- comment: Chart of accounts (OHADA plan comptable) for tnt-accounting-core
CREATE TABLE IF NOT EXISTS accounting.accounts (
    id                UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    code              VARCHAR(20)   NOT NULL,
    name              VARCHAR(255)  NOT NULL,
    type              VARCHAR(30)   NOT NULL,
    category          VARCHAR(60)   NOT NULL,
    currency          CHAR(3)       NOT NULL DEFAULT 'XAF',
    balance           NUMERIC(19,4) NOT NULL DEFAULT 0,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    parent_account_id UUID,
    ohada_class       INTEGER       NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_accounts_tenant_code UNIQUE (tenant_id, code)
);
CREATE INDEX IF NOT EXISTS idx_accounts_tenant_id       ON accounting.accounts (tenant_id);
CREATE INDEX IF NOT EXISTS idx_accounts_tenant_code     ON accounting.accounts (tenant_id, code);
CREATE INDEX IF NOT EXISTS idx_accounts_tenant_category ON accounting.accounts (tenant_id, category);
-- rollback DROP TABLE IF EXISTS accounting.accounts;
