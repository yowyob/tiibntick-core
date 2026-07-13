--liquibase formatted sql
--changeset jeff-belekotan:002_create_device_registrations
--comment: Agency-scoped device sync cursor registry for offline mobile clients

CREATE TABLE IF NOT EXISTS agency_sync.device_registrations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    agency_id       UUID NOT NULL REFERENCES agency_org.agencies(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL,
    device_id       VARCHAR(255) NOT NULL,
    last_sync_token TEXT,
    registered_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_agency_sync_device UNIQUE (tenant_id, agency_id, user_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_agency_sync_user ON agency_sync.device_registrations (tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_agency_sync_agency ON agency_sync.device_registrations (agency_id);

ALTER TABLE agency_sync.device_registrations ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_sync.device_registrations CASCADE;
