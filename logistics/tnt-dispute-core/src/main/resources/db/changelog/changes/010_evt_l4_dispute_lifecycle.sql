--liquibase formatted sql
--changeset evt-l4:evt-l4-001-dispute-lifecycle labels:evt-l4
--comment: Lot L4 (evt-litiges): dispute lifecycle published on tnt.dispute.events — party at fault of the ruling ([B.6]),
--comment: incident escalated into the dispute, closing time, and the aggregateVersion of the last published event.
ALTER TABLE tnt_disputes
    ADD COLUMN IF NOT EXISTS responsible_party VARCHAR(20),
    ADD COLUMN IF NOT EXISTS incident_id       VARCHAR(36),
    ADD COLUMN IF NOT EXISTS closed_at         TIMESTAMP,
    ADD COLUMN IF NOT EXISTS event_sequence    BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN tnt_disputes.responsible_party IS 'Lot L4: party at fault set by the ruling (pays the return / the new delivery).';
COMMENT ON COLUMN tnt_disputes.incident_id IS 'Lot L4: incident escalated into this dispute (automatic opening LT1), no physical FK.';
COMMENT ON COLUMN tnt_disputes.closed_at IS 'Lot L4: when the dispute reached a terminal state.';
COMMENT ON COLUMN tnt_disputes.event_sequence IS 'Lot L4: aggregateVersion of the last event published on tnt.dispute.events.';
--rollback ALTER TABLE tnt_disputes DROP COLUMN IF EXISTS event_sequence, DROP COLUMN IF EXISTS closed_at, DROP COLUMN IF EXISTS incident_id, DROP COLUMN IF EXISTS responsible_party;

--changeset evt-l4:evt-l4-002-dispute-closed-at-backfill labels:evt-l4
--comment: Lot L4: disputes already terminal get closed_at = their last update.
UPDATE tnt_disputes SET closed_at = updated_at
WHERE closed_at IS NULL AND status IN ('COMPENSATED', 'CLOSED_RESOLVED', 'CLOSED_WITHDRAWN', 'CLOSED_EXPIRED');
--rollback UPDATE tnt_disputes SET closed_at = NULL;

