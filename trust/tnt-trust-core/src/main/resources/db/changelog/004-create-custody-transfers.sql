-- ============================================================
-- Changeset 004: Custody Transfers cache table
-- Author: MANFOUO Braun | Module: tnt-trust
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:004-create-custody-transfers

CREATE TABLE IF NOT EXISTS tnt_trust.custody_transfers (
    transfer_id          VARCHAR(36)    NOT NULL,
    package_id           VARCHAR(36)    NOT NULL,
    tracking_code        VARCHAR(50)    NOT NULL,
    tenant_id            VARCHAR(36)    NOT NULL,
    from_actor_id        VARCHAR(36)    NOT NULL,
    to_actor_id          VARCHAR(36)    NOT NULL,
    transfer_type        VARCHAR(30)    NOT NULL,
    hub_id               VARCHAR(36)    NULL,
    transferred_at       TIMESTAMPTZ    NOT NULL,
    blockchain_tx_hash   VARCHAR(66)    NULL,

    CONSTRAINT pk_custody_transfers PRIMARY KEY (transfer_id),
    CONSTRAINT chk_custody_transfer_type CHECK (
        transfer_type IN (
            'PICKUP_FROM_SENDER','TRANSFER_TO_HUB','PICKUP_FROM_HUB',
            'TRANSFER_TO_RECIPIENT','RETURN_TO_SENDER'))
);

COMMENT ON TABLE tnt_trust.custody_transfers IS
    'Local cache of custody transfers — package chain of custody. '
    'Forms the "Fil d''Ariane" together with delivery_proofs.';

-- rollback DROP TABLE IF EXISTS tnt_trust.custody_transfers;
