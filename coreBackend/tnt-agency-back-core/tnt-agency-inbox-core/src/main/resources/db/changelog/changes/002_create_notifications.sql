--liquibase formatted sql
--changeset jeff-belekotan:002_create_notifications
--comment: Ported from tnt-agency V009

CREATE TABLE IF NOT EXISTS agency_inbox.notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    agency_id   UUID NOT NULL REFERENCES agency_org.agencies(id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL,
    event_type  VARCHAR(50) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        TEXT NOT NULL,
    href        VARCHAR(255) NOT NULL DEFAULT '/',
    is_read     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_inbox_tenant_agency ON agency_inbox.notifications (tenant_id, agency_id);
CREATE INDEX IF NOT EXISTS idx_inbox_created_at ON agency_inbox.notifications (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_inbox_unread ON agency_inbox.notifications (agency_id, is_read);

ALTER TABLE agency_inbox.notifications ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_inbox.notifications CASCADE;
