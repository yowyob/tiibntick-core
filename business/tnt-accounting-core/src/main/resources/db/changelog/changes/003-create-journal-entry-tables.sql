-- liquibase formatted sql
-- changeset manfouo-braun:003-journal-entry-tables
-- comment: Journal entries (header + lines) for OHADA double-entry bookkeeping
CREATE TABLE IF NOT EXISTS accounting.journal_entries (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    organization_id     UUID          NOT NULL,
    number              VARCHAR(50)   NOT NULL,
    type                VARCHAR(30)   NOT NULL,
    reference_type      VARCHAR(50),
    reference_id        VARCHAR(100),
    description         TEXT,
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_by_user_id  VARCHAR(255),
    posted_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_journal_entries_tenant_number UNIQUE (tenant_id, number)
);
CREATE INDEX IF NOT EXISTS idx_je_tenant_org   ON accounting.journal_entries (tenant_id, organization_id);
CREATE INDEX IF NOT EXISTS idx_je_reference_id ON accounting.journal_entries (tenant_id, reference_id);
CREATE INDEX IF NOT EXISTS idx_je_status       ON accounting.journal_entries (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_je_created_at   ON accounting.journal_entries (created_at);

CREATE TABLE IF NOT EXISTS accounting.journal_entry_lines (
    id               UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    journal_entry_id UUID          NOT NULL REFERENCES accounting.journal_entries(id) ON DELETE CASCADE,
    line_number      INTEGER       NOT NULL,
    account_id       UUID,
    account_code     VARCHAR(20)   NOT NULL,
    label            VARCHAR(500)  NOT NULL,
    debit_amount     NUMERIC(19,4) NOT NULL DEFAULT 0,
    credit_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency         CHAR(3)       NOT NULL DEFAULT 'XAF',
    CONSTRAINT uq_jel_entry_line UNIQUE (journal_entry_id, line_number),
    CONSTRAINT chk_jel_amounts CHECK (
        (debit_amount > 0 AND credit_amount = 0) OR
        (credit_amount > 0 AND debit_amount = 0)
    )
);
CREATE INDEX IF NOT EXISTS idx_jel_journal_entry_id ON accounting.journal_entry_lines (journal_entry_id);
CREATE INDEX IF NOT EXISTS idx_jel_account_code     ON accounting.journal_entry_lines (account_code);
-- rollback DROP TABLE IF EXISTS accounting.journal_entry_lines; DROP TABLE IF EXISTS accounting.journal_entries;
