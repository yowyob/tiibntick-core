-- ============================================================
-- Migration: 001_create_billing_report_tables.sql
-- Module   : tnt-billing-report
-- Author   : MANFOUO Braun
-- ============================================================

-- Invoice report projection (read-optimised, updated from Kafka events)
CREATE TABLE IF NOT EXISTS tnt_invoice_report_entries (
    invoice_id      UUID          NOT NULL PRIMARY KEY,
    invoice_number  VARCHAR(60)   NOT NULL,
    tenant_id       UUID          NOT NULL,
    country_code    VARCHAR(3)    NOT NULL DEFAULT 'CM',
    client_id       VARCHAR(100)  NOT NULL,
    mission_id      VARCHAR(100)  NOT NULL DEFAULT '',
    gross_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(19,4) NOT NULL DEFAULT 0,
    net_amount      NUMERIC(19,4) NOT NULL DEFAULT 0,
    platform_fee    NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency        VARCHAR(5)    NOT NULL DEFAULT 'XAF',
    status          VARCHAR(30)   NOT NULL,
    invoice_date    DATE          NOT NULL,
    paid_date       DATE
);

CREATE INDEX IF NOT EXISTS idx_rpt_entries_tenant       ON tnt_invoice_report_entries (tenant_id);
CREATE INDEX IF NOT EXISTS idx_rpt_entries_tenant_date  ON tnt_invoice_report_entries (tenant_id, invoice_date);
CREATE INDEX IF NOT EXISTS idx_rpt_entries_status       ON tnt_invoice_report_entries (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_rpt_entries_country      ON tnt_invoice_report_entries (tenant_id, country_code);
CREATE INDEX IF NOT EXISTS idx_rpt_entries_paid_date    ON tnt_invoice_report_entries (tenant_id, paid_date) WHERE paid_date IS NOT NULL;

-- Billing KPI snapshots (point-in-time metrics)
CREATE TABLE IF NOT EXISTS tnt_billing_kpi_snapshots (
    id                       UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id                UUID          NOT NULL,
    open_invoices_count      BIGINT        NOT NULL DEFAULT 0,
    overdue_invoices_count   BIGINT        NOT NULL DEFAULT 0,
    paid_invoices_today      BIGINT        NOT NULL DEFAULT 0,
    generated_invoices_today BIGINT        NOT NULL DEFAULT 0,
    outstanding_amount       NUMERIC(19,4) NOT NULL DEFAULT 0,
    collected_today          NUMERIC(19,4) NOT NULL DEFAULT 0,
    generated_today          NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency                 VARCHAR(5)    NOT NULL DEFAULT 'XAF',
    day_collection_rate      NUMERIC(6,2)  NOT NULL DEFAULT 0,
    mtd_collection_rate      NUMERIC(6,2)  NOT NULL DEFAULT 0,
    avg_invoice_value        NUMERIC(19,4) NOT NULL DEFAULT 0,
    avg_days_to_pay          BIGINT        NOT NULL DEFAULT 0,
    snapshot_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kpi_snapshots_tenant     ON tnt_billing_kpi_snapshots (tenant_id, snapshot_at DESC);
