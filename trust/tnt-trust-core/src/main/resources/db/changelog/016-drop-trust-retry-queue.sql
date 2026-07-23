-- ============================================================
-- Changeset 016: Drop trust_retry_queue table
-- Author: MANFOUO Braun | Module: tnt-trust
-- Context: Chantier C · Audit n°3 · P5 — transactional outbox migration.
--
-- The trust_retry_queue catch-up mechanism (changeset 011, §15.5 of the
-- Trust connexion design) is superseded by yow-event-kernel's transactional
-- outbox: KafkaTrustEventPublisherAdapter now persists every trust event as
-- an event_bus outbox envelope inside the business transaction, and the
-- outbox poller owns Kafka delivery, retry, and DLQ. The guard/circuit
-- breaker/drainer classes were removed along with this table (no production
-- data existed in any environment at migration time).
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:016-drop-trust-retry-queue

DROP INDEX IF EXISTS tnt_trust.idx_trust_retry_queue_created_at;

DROP TABLE IF EXISTS tnt_trust.trust_retry_queue;

-- rollback CREATE TABLE IF NOT EXISTS tnt_trust.trust_retry_queue (retry_id UUID NOT NULL DEFAULT gen_random_uuid(), message_key VARCHAR(255) NOT NULL, message_payload TEXT NOT NULL, event_type VARCHAR(80) NOT NULL, attempt_count INT NOT NULL DEFAULT 0, failure_reason TEXT, created_at TIMESTAMP NOT NULL DEFAULT NOW(), CONSTRAINT pk_trust_retry_queue PRIMARY KEY (retry_id));
-- rollback CREATE INDEX IF NOT EXISTS idx_trust_retry_queue_created_at ON tnt_trust.trust_retry_queue (created_at);
