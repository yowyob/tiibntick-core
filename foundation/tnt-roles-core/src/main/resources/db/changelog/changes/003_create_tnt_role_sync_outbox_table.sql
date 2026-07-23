--liquibase formatted sql
--changeset MANFOUO_Braun:003_create_tnt_role_sync_outbox_table
--comment: Create tnt_role_sync_outbox table — transactional outbox for local RBAC writes awaiting sync to the Kernel (Chantier D · Audit n°6 · S5)

CREATE TABLE IF NOT EXISTS tnt_role_sync_outbox (
    id                  UUID            NOT NULL PRIMARY KEY,
    operation           VARCHAR(24)     NOT NULL,  -- PROVISION_ROLE | DELETE_ROLE | ASSIGN_ROLE | REVOKE_ASSIGNMENT
    aggregate_type      VARCHAR(16)     NOT NULL,  -- ROLE | ASSIGNMENT
    aggregate_id        UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    payload             TEXT            NOT NULL,  -- JSON snapshot, parsed by the worker (built in a later phase)
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    attempt_count       INT             NOT NULL DEFAULT 0,
    last_error          TEXT,
    kernel_ref_id       UUID,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    next_attempt_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    processed_at        TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON tnt_role_sync_outbox(status, next_attempt_at)
    WHERE status IN ('PENDING', 'RETRYING');

--rollback DROP TABLE IF EXISTS tnt_role_sync_outbox;
