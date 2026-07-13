--liquibase formatted sql
--changeset jeff-belekotan:002_create_client_intake_requests
--comment: Ported from tnt-agency V016

CREATE TABLE IF NOT EXISTS agency_intake.client_intake_requests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    agency_id         UUID NOT NULL REFERENCES agency_org.agencies(id) ON DELETE CASCADE,
    branch_id         UUID NOT NULL,
    reference_code    VARCHAR(32) NOT NULL UNIQUE,
    source            VARCHAR(20) NOT NULL DEFAULT 'MOBILE',
    status            VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    sender_name       VARCHAR(255) NOT NULL,
    sender_phone      VARCHAR(30),
    recipient_name    VARCHAR(255) NOT NULL,
    recipient_phone   VARCHAR(30),
    pickup_address    TEXT,
    delivery_address  TEXT,
    weight_kg         DOUBLE PRECISION,
    packages_count    INT NOT NULL DEFAULT 1,
    delivery_mode     VARCHAR(20) NOT NULL DEFAULT 'DIRECT',
    target_hub_id     UUID,
    notes             TEXT,
    mission_id        UUID,
    tracking_code     VARCHAR(64),
    rejection_reason  TEXT,
    reviewed_by       UUID,
    reviewed_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_intake_reference ON agency_intake.client_intake_requests (reference_code);
CREATE INDEX IF NOT EXISTS idx_intake_agency_status ON agency_intake.client_intake_requests (agency_id, status);
CREATE INDEX IF NOT EXISTS idx_intake_tenant ON agency_intake.client_intake_requests (tenant_id);

ALTER TABLE agency_intake.client_intake_requests ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_intake.client_intake_requests CASCADE;
