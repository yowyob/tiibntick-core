-- ============================================================
-- Changeset 011: Create trust_retry_queue table
-- Author: MANFOUO Braun | Module: tnt-trust
-- Context: §15.5 of TNT_CORE_Connexion_Trust_Module.md — resilience design.
--
-- Catch-up queue absorbing yow.trust.events publications made while the
-- gateway (Kafka broker or yow-trust-event) is degraded, so no
-- LogisticTrustEvent is ever lost. Rows store the pre-serialized Kafka
-- wire envelope, not the domain object, so TrustRetryQueueDrainer can
-- republish verbatim without reconstructing a LogisticTrustEvent.
--
-- A row's mere presence means "pending" — TrustRetryQueueDrainer deletes
-- the row once redelivery succeeds (no separate status column needed).
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:011-create-trust-retry-queue

CREATE TABLE IF NOT EXISTS tnt_trust.trust_retry_queue (

    -- Primary key: auto-generated UUID for each queued envelope
    retry_id            UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Kafka partition key (the original LogisticTrustEvent.entityId)
    message_key         VARCHAR(255)    NOT NULL,

    -- Pre-serialized TrustEventKafkaMessage JSON envelope, republished verbatim
    message_payload     TEXT            NOT NULL,

    -- LogisticTrustEventType name — observability only, not used for replay
    event_type          VARCHAR(80)     NOT NULL,

    -- Number of prior failed drain attempts
    attempt_count        INT             NOT NULL DEFAULT 0,

    -- Reason the last publish/drain attempt failed (or was skipped)
    failure_reason       TEXT,

    -- UTC timestamp when this row was first enqueued — drain order (FIFO)
    created_at           TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_trust_retry_queue PRIMARY KEY (retry_id)
);

COMMENT ON TABLE tnt_trust.trust_retry_queue IS
    'Catch-up queue for yow.trust.events publications made while the gateway '
    'is degraded (Kafka broker or yow-trust-event down). Drained by '
    'TrustRetryQueueDrainer using FOR UPDATE SKIP LOCKED for multi-replica safety. '
    'A row existing means pending; the row is deleted on successful redelivery.';

COMMENT ON COLUMN tnt_trust.trust_retry_queue.message_payload IS
    'Pre-serialized TrustEventKafkaMessage JSON envelope (see TrustEventEnvelopeMapper), '
    'republished verbatim to yow.trust.events without LogisticTrustEvent reconstruction.';

-- Drain order: oldest pending row first
CREATE INDEX IF NOT EXISTS idx_trust_retry_queue_created_at
    ON tnt_trust.trust_retry_queue (created_at);

-- rollback DROP TABLE IF EXISTS tnt_trust.trust_retry_queue;
