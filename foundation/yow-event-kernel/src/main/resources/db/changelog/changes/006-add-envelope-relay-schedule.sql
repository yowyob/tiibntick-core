--liquibase formatted sql
--changeset MANFOUO_Braun:006-add-envelope-relay-schedule
--comment: R2 (2026-11-26) — automatic outbox relay: next_attempt_at drives the exponential backoff of FAILED envelopes, the take-over of PENDING envelopes left behind by a crash and the expiry of a RETRYING claim
--
-- The column is NOT mapped on DomainEventEnvelopeEntity: inserts never send it, so DEFAULT NOW()
-- (database clock) is the creation time of every new envelope. Existing rows get NOW() as well,
-- which only matters for envelopes still PENDING/FAILED: the relay takes them over (at-least-once).

ALTER TABLE event_bus.domain_event_envelopes
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_dee_relay_due
    ON event_bus.domain_event_envelopes (next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED', 'RETRYING');

--rollback DROP INDEX IF EXISTS event_bus.idx_dee_relay_due;
--rollback ALTER TABLE event_bus.domain_event_envelopes DROP COLUMN IF EXISTS next_attempt_at;
