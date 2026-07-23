-- ============================================================
-- Changeset 015: Proof-of-Location Verifications cache table
-- Author: MANFOUO Braun | Module: tnt-trust
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:015-create-pol-verifications

CREATE TABLE IF NOT EXISTS tnt_trust.pol_verifications (
    event_id             VARCHAR(36)      NOT NULL,
    actor_id             VARCHAR(36)      NOT NULL,
    tenant_id            VARCHAR(36)      NOT NULL,
    gps_lat              DOUBLE PRECISION NOT NULL,
    gps_lng              DOUBLE PRECISION NOT NULL,
    pol_hash             VARCHAR(128)     NOT NULL,
    verified_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    blockchain_tx_hash   VARCHAR(66)      NULL,

    CONSTRAINT pk_pol_verifications PRIMARY KEY (event_id)
);

COMMENT ON TABLE tnt_trust.pol_verifications IS
    'Local cache of on-chain Proof-of-Location verifications. '
    'Populated when yow.trust.events.committed confirms PROOF_OF_LOCATION_VERIFIED events.';

-- rollback DROP TABLE IF EXISTS tnt_trust.pol_verifications;
