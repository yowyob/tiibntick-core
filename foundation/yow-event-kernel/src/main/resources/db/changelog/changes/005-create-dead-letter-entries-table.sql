--liquibase formatted sql
--changeset MANFOUO_Braun:005-create-dead-letter-entries-table
--comment: Create event_bus.dead_letter_entries — DLQ table backing DeadLetterEntryEntity / DeadLetterRepository

CREATE TABLE IF NOT EXISTS event_bus.dead_letter_entries
(
    id                   VARCHAR(36)  NOT NULL,
    original_envelope_id VARCHAR(36)  NOT NULL,
    kafka_topic          VARCHAR(200) NOT NULL,
    original_payload     TEXT         NOT NULL,
    failure_reason       TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    failed_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    reprocessed_at       TIMESTAMP,
    discard_reason       TEXT,

    CONSTRAINT pk_dead_letter_entries PRIMARY KEY (id),
    CONSTRAINT fk_dead_letter_entries_envelope FOREIGN KEY (original_envelope_id)
        REFERENCES event_bus.domain_event_envelopes (id)
);

CREATE INDEX IF NOT EXISTS idx_dlq_status
    ON event_bus.dead_letter_entries (status, failed_at);

CREATE INDEX IF NOT EXISTS idx_dlq_topic
    ON event_bus.dead_letter_entries (kafka_topic, status);

--rollback DROP TABLE IF EXISTS event_bus.dead_letter_entries;
