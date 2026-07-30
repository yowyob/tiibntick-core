--liquibase formatted sql
--changeset jeff-belekotan:007_hub_operator_and_handoffs
--comment: Hub operator assignment, depositor tracking, handoff requests (deposit/withdraw validation)

ALTER TABLE agency_org.agency_relay_hubs
    ADD COLUMN IF NOT EXISTS operator_user_id UUID,
    ADD COLUMN IF NOT EXISTS operator_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS operator_name VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_agency_hubs_operator
    ON agency_org.agency_relay_hubs (operator_user_id);

ALTER TABLE agency_org.hub_parcel_records
    ADD COLUMN IF NOT EXISTS deposited_by_actor_id UUID,
    ADD COLUMN IF NOT EXISTS deposited_by_label VARCHAR(255),
    ADD COLUMN IF NOT EXISTS withdrawn_by_actor_id UUID;

CREATE TABLE IF NOT EXISTS agency_org.hub_handoff_requests (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    agency_id               UUID NOT NULL,
    hub_id                  UUID NOT NULL REFERENCES agency_org.agency_relay_hubs(id) ON DELETE CASCADE,
    handoff_type            VARCHAR(20) NOT NULL,
    status                  VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    mission_id              UUID,
    package_id              UUID,
    tracking_code           VARCHAR(50) NOT NULL,
    requester_actor_id      UUID,
    requester_role          VARCHAR(30),
    requester_label         VARCHAR(255),
    withdraw_party          VARCHAR(30),
    validated_by_actor_id   UUID,
    validated_by_label      VARCHAR(255),
    notes                   VARCHAR(500),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    validated_at            TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_hub_handoff_hub_status
    ON agency_org.hub_handoff_requests (hub_id, status);
CREATE INDEX IF NOT EXISTS idx_hub_handoff_tracking
    ON agency_org.hub_handoff_requests (tracking_code);
CREATE INDEX IF NOT EXISTS idx_hub_handoff_tenant
    ON agency_org.hub_handoff_requests (tenant_id, agency_id);

ALTER TABLE agency_org.hub_handoff_requests ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_org.hub_handoff_requests CASCADE;
--rollback ALTER TABLE agency_org.hub_parcel_records DROP COLUMN IF EXISTS deposited_by_actor_id;
--rollback ALTER TABLE agency_org.hub_parcel_records DROP COLUMN IF EXISTS deposited_by_label;
--rollback ALTER TABLE agency_org.hub_parcel_records DROP COLUMN IF EXISTS withdrawn_by_actor_id;
--rollback ALTER TABLE agency_org.agency_relay_hubs DROP COLUMN IF EXISTS operator_user_id;
--rollback ALTER TABLE agency_org.agency_relay_hubs DROP COLUMN IF EXISTS operator_email;
--rollback ALTER TABLE agency_org.agency_relay_hubs DROP COLUMN IF EXISTS operator_name;
