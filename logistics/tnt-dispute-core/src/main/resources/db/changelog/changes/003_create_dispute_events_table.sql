--liquibase formatted sql
--changeset MANFOUO_Braun:003_create_dispute_events_table
--comment: Create tnt_dispute_events table — immutable audit timeline per dispute

CREATE TABLE IF NOT EXISTS tnt_dispute_events (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    dispute_id          VARCHAR(36)     NOT NULL,
    tenant_id           VARCHAR(100)    NOT NULL,
    type                VARCHAR(50)     NOT NULL,
    description         TEXT,
    performed_by        VARCHAR(100),
    performed_by_type   VARCHAR(30)     NOT NULL,
    occurred_at         TIMESTAMP       NOT NULL DEFAULT NOW(),
    metadata_json       TEXT,

    CONSTRAINT fk_event_dispute
        FOREIGN KEY (dispute_id) REFERENCES tnt_disputes(id) ON DELETE CASCADE
);

--rollback DROP TABLE IF EXISTS tnt_dispute_events;
