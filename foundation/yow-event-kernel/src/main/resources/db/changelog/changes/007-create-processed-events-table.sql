--liquibase formatted sql
--changeset MANFOUO_Braun:007-create-processed-events-table
--comment: 2026-11-26 — every Kafka consumer is idempotent: event_bus.processed_events records, per consumer group and topic, the events already processed successfully (filled after success, purged after the configured retention)
--
-- event_id: the X-Yow-Envelope-Id header (outbox envelopes — identical on every relay re-send),
-- else an X-Yow-Event-Id / eventId header, else the top-level "eventId" of the JSON payload, else
-- "tpo:" + SHA-256(topic|partition|offset|timestamp) (catches redeliveries of the same record only).
-- Lengths: Kafka caps topic names at 249 characters; ids longer than 128 are stored hashed.

CREATE TABLE IF NOT EXISTS event_bus.processed_events
(
    consumer_group VARCHAR(255) NOT NULL,
    topic          VARCHAR(249) NOT NULL,
    event_id       VARCHAR(128) NOT NULL,
    processed_at   TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_processed_events PRIMARY KEY (consumer_group, topic, event_id)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON event_bus.processed_events (processed_at);

--rollback DROP TABLE IF EXISTS event_bus.processed_events;
