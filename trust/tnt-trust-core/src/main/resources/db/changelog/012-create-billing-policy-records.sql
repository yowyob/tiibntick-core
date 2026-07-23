-- ============================================================
-- Changeset 012: Billing Policy on-chain anchor records cache table
-- Author: MANFOUO Braun | Module: tnt-trust
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:012-create-billing-policy-records

CREATE TABLE IF NOT EXISTS tnt_trust.billing_policy_records (
    record_id            VARCHAR(36)    NOT NULL,
    policy_id            VARCHAR(36)    NOT NULL,
    agency_id            VARCHAR(36)    NOT NULL,
    tenant_id            VARCHAR(36)    NOT NULL,
    policy_summary_json  TEXT           NULL,
    activated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    blockchain_tx_hash   VARCHAR(66)    NULL,

    CONSTRAINT pk_billing_policy_records PRIMARY KEY (record_id)
);

CREATE INDEX IF NOT EXISTS idx_billing_policy_records_policy_id
    ON tnt_trust.billing_policy_records (policy_id, activated_at DESC);

COMMENT ON TABLE tnt_trust.billing_policy_records IS
    'Local cache of on-chain billing policy activation records. '
    'Populated at activation time; blockchain_tx_hash is filled in when '
    'yow.trust.events.committed confirms the BILLING_POLICY_ACTIVATED event.';

-- rollback DROP TABLE IF EXISTS tnt_trust.billing_policy_records;
