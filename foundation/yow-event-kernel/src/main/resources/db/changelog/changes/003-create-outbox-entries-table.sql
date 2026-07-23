--liquibase formatted sql
--changeset MANFOUO_Braun:003-create-outbox-entries-table
--comment: Create event_bus.outbox_entries — transactional outbox staging table backing OutboxEntryEntity / OutboxEntryRepository
--
-- headers stored as TEXT (JSON string), not JSONB — see the note in
-- 002-create-domain-event-envelopes-table.sql (R2DBC driver cannot bind a
-- plain Java String into a jsonb column).

CREATE TABLE IF NOT EXISTS event_bus.outbox_entries
(
    id                  VARCHAR(36)  NOT NULL,
    envelope_id         VARCHAR(36)  NOT NULL,
    tenant_id           VARCHAR(36)  NOT NULL,
    kafka_topic         VARCHAR(200) NOT NULL,
    kafka_partition_key VARCHAR(100) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    scheduled_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMP,
    processing_attempt  INTEGER      NOT NULL DEFAULT 0,
    headers             TEXT         NOT NULL DEFAULT '{}',

    CONSTRAINT pk_outbox_entries PRIMARY KEY (id),
    CONSTRAINT fk_outbox_entries_envelope FOREIGN KEY (envelope_id)
        REFERENCES event_bus.domain_event_envelopes (id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_status
    ON event_bus.outbox_entries (status, scheduled_at);

CREATE INDEX IF NOT EXISTS idx_outbox_envelope
    ON event_bus.outbox_entries (envelope_id);

--rollback DROP TABLE IF EXISTS event_bus.outbox_entries;
