-- ============================================================
-- Migration: 001_create_billing_invoice_tables.sql
-- Module   : tnt-billing-invoice
-- Author   : MANFOUO Braun
-- ============================================================

-- Invoice sequence tracker (atomic counter per tenant-year)
CREATE TABLE IF NOT EXISTS tnt_invoice_sequences (
    seq_key       VARCHAR(80)  NOT NULL PRIMARY KEY,
    current_value BIGINT       NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Main invoices table
CREATE TABLE IF NOT EXISTS tnt_invoices (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    invoice_number          VARCHAR(60)  NOT NULL UNIQUE,
    tenant_id               UUID         NOT NULL,
    tenant_code             VARCHAR(30)  NOT NULL,
    country_code            VARCHAR(3)   NOT NULL DEFAULT 'CM',
    mission_id              VARCHAR(100),
    sales_order_id          VARCHAR(100),
    client_id               VARCHAR(100) NOT NULL,

    -- Line items, tax lines, discounts stored as JSONB
    lines_json              JSONB        NOT NULL DEFAULT '[]',
    tax_lines_json          JSONB        NOT NULL DEFAULT '[]',
    discounts_json          JSONB        NOT NULL DEFAULT '[]',

    -- Monetary totals (split into amount + currency for type safety)
    subtotal_ex_tax_amount   NUMERIC(19,4) NOT NULL,
    subtotal_ex_tax_currency VARCHAR(5)    NOT NULL DEFAULT 'XAF',
    total_tax_amount         NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_tax_currency       VARCHAR(5)    NOT NULL DEFAULT 'XAF',
    total_inc_tax_amount     NUMERIC(19,4) NOT NULL,
    total_inc_tax_currency   VARCHAR(5)    NOT NULL DEFAULT 'XAF',
    net_amount_amount        NUMERIC(19,4) NOT NULL,
    net_amount_currency      VARCHAR(5)    NOT NULL DEFAULT 'XAF',

    -- Lifecycle
    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    pdf_storage_key     TEXT,
    issued_at           TIMESTAMPTZ,
    due_at              TIMESTAMPTZ,
    paid_at             TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason TEXT,
    credit_note_ref     VARCHAR(100),

    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version     INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_tnt_invoices_tenant    ON tnt_invoices (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tnt_invoices_mission   ON tnt_invoices (mission_id);
CREATE INDEX IF NOT EXISTS idx_tnt_invoices_client    ON tnt_invoices (tenant_id, client_id);
CREATE INDEX IF NOT EXISTS idx_tnt_invoices_status    ON tnt_invoices (status);
CREATE INDEX IF NOT EXISTS idx_tnt_invoices_due_at    ON tnt_invoices (due_at) WHERE status IN ('ISSUED','PARTIALLY_PAID');

-- Credit notes
CREATE TABLE IF NOT EXISTS tnt_credit_notes (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    original_invoice_id  UUID         NOT NULL REFERENCES tnt_invoices (id) ON DELETE RESTRICT,
    tenant_id            UUID         NOT NULL,
    amount_value         NUMERIC(19,4) NOT NULL,
    amount_currency      VARCHAR(5)    NOT NULL DEFAULT 'XAF',
    reason               TEXT         NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ISSUED',
    issued_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    applied_at           TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_tnt_credit_notes_invoice ON tnt_credit_notes (original_invoice_id);
CREATE INDEX IF NOT EXISTS idx_tnt_credit_notes_tenant  ON tnt_credit_notes (tenant_id);
