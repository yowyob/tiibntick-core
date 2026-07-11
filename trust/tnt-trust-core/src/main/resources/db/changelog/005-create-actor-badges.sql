-- ============================================================
-- Changeset 005: Actor Badges cache table
-- Author: MANFOUO Braun | Module: tnt-trust
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:005-create-actor-badges

CREATE TABLE IF NOT EXISTS tnt_trust.actor_badges (
    badge_id             VARCHAR(36)    NOT NULL,
    actor_id             VARCHAR(36)    NOT NULL,
    tenant_id            VARCHAR(36)    NOT NULL,
    badge_type           VARCHAR(100)   NOT NULL,
    points               INTEGER        NOT NULL DEFAULT 0,
    awarded_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    blockchain_tx_hash   VARCHAR(66)    NULL,
    revoked              BOOLEAN        NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_actor_badges PRIMARY KEY (badge_id)
);

COMMENT ON TABLE tnt_trust.actor_badges IS
    'Local cache of on-chain actor reputation badges. '
    'Populated when yow.trust.events.committed confirms BADGE_AWARDED events.';

-- rollback DROP TABLE IF EXISTS tnt_trust.actor_badges;
