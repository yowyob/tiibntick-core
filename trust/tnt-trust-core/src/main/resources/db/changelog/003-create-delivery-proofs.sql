-- ============================================================
-- Changeset 003: Delivery Proofs cache table
-- Author: MANFOUO Braun | Module: tnt-trust
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:003-create-delivery-proofs

CREATE TABLE IF NOT EXISTS tnt_trust.delivery_proofs (
    proof_id             VARCHAR(36)    NOT NULL,
    mission_id           VARCHAR(36)    NOT NULL,
    package_id           VARCHAR(36)    NOT NULL,
    actor_id             VARCHAR(36)    NOT NULL,
    tenant_id            VARCHAR(36)    NOT NULL,
    photo_hash           VARCHAR(66)    NOT NULL,
    signature_hash       VARCHAR(66)    NULL,
    gps_lat              DOUBLE PRECISION NOT NULL,
    gps_lng              DOUBLE PRECISION NOT NULL,
    confirmed_at         TIMESTAMPTZ    NOT NULL,
    blockchain_tx_hash   VARCHAR(66)    NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_delivery_proofs PRIMARY KEY (proof_id)
);

COMMENT ON TABLE tnt_trust.delivery_proofs IS
    'Local cache of delivery proofs — Fil d''Ariane. '
    'blockchain_tx_hash is populated asynchronously after Fabric commit confirmation.';

-- rollback DROP TABLE IF EXISTS tnt_trust.delivery_proofs;
