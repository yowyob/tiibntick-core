-- ============================================================
-- Changeset 014: DAO Rule Records cache table
-- Author: MANFOUO Braun | Module: tnt-trust
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:014-create-dao-rule-records

CREATE TABLE IF NOT EXISTS tnt_trust.dao_rule_records (
    rule_id              VARCHAR(36)    NOT NULL,
    zone_id              VARCHAR(36)    NOT NULL,
    tenant_id            VARCHAR(36)    NOT NULL,
    rule_json            TEXT           NOT NULL,
    activated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    blockchain_tx_hash   VARCHAR(66)    NULL,

    CONSTRAINT pk_dao_rule_records PRIMARY KEY (rule_id)
);

COMMENT ON TABLE tnt_trust.dao_rule_records IS
    'Local cache of on-chain DAO zone governance rule activations. '
    'Populated when yow.trust.events.committed confirms DAO_RULE_ACTIVATED events.';

-- rollback DROP TABLE IF EXISTS tnt_trust.dao_rule_records;
