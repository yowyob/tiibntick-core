--liquibase formatted sql
--changeset MANFOUO_Braun:evt-l6-001-kernel-bookkeeping-links
--comment: Lot L6 (decision H.3, QO-20, QO-21 of 2026-11-26): the books are written in the Kernel accounting service. Local correspondences: Kernel account / journal per owner book (PLATFORM or ORGANIZATION), and one row per booked fact (source_key unique = idempotent replay).

CREATE TABLE IF NOT EXISTS accounting.kernel_account_links (
    id                 UUID         PRIMARY KEY,
    owner_type         VARCHAR(20)  NOT NULL,
    owner_id           UUID         NOT NULL,
    code               VARCHAR(30)  NOT NULL,
    kernel_account_id  UUID         NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_kernel_account_link UNIQUE (owner_type, owner_id, code)
);

CREATE TABLE IF NOT EXISTS accounting.kernel_journal_links (
    id                 UUID         PRIMARY KEY,
    owner_type         VARCHAR(20)  NOT NULL,
    owner_id           UUID         NOT NULL,
    code               VARCHAR(30)  NOT NULL,
    kernel_journal_id  UUID         NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_kernel_journal_link UNIQUE (owner_type, owner_id, code)
);

CREATE TABLE IF NOT EXISTS accounting.kernel_entry_links (
    id                 UUID           PRIMARY KEY,
    source_key         VARCHAR(120)   NOT NULL,
    source_type        VARCHAR(40)    NOT NULL,
    source_event_id    VARCHAR(80),
    owner_type         VARCHAR(20),
    owner_id           UUID,
    reference          VARCHAR(160),
    amount             NUMERIC(19, 4),
    currency           VARCHAR(3),
    fact               TEXT           NOT NULL,
    kernel_entry_id    UUID,
    status             VARCHAR(20)    NOT NULL,
    reason             VARCHAR(255),
    posted_at          TIMESTAMP,
    created_at         TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP      NOT NULL DEFAULT now(),
    CONSTRAINT uq_kernel_entry_link_source UNIQUE (source_key)
);

CREATE INDEX IF NOT EXISTS idx_kernel_entry_links_status ON accounting.kernel_entry_links (status, created_at);

--rollback DROP TABLE IF EXISTS accounting.kernel_entry_links; DROP TABLE IF EXISTS accounting.kernel_journal_links; DROP TABLE IF EXISTS accounting.kernel_account_links;
