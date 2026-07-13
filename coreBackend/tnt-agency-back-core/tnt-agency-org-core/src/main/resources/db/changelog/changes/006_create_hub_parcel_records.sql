--liquibase formatted sql
--changeset jeff-belekotan:006_create_hub_parcel_records
--comment: Agency hub parcel index — movements orchestrated via tnt-inventory-core

CREATE TABLE IF NOT EXISTS agency_org.hub_parcel_records (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    hub_id                      UUID NOT NULL REFERENCES agency_org.agency_relay_hubs(id) ON DELETE CASCADE,
    package_id                  UUID NOT NULL,
    mission_id                  UUID,
    tracking_code               VARCHAR(50) NOT NULL,
    deposited_at                TIMESTAMPTZ NOT NULL,
    withdrawal_deadline         TIMESTAMPTZ NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'DEPOSITED',
    identity_verified           BOOLEAN NOT NULL DEFAULT FALSE,
    withdrawn_by                VARCHAR(255),
    core_hub_package_entry_id   UUID,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_hub_parcel_hub ON agency_org.hub_parcel_records (hub_id);
CREATE INDEX IF NOT EXISTS idx_hub_parcel_tracking ON agency_org.hub_parcel_records (tracking_code);
CREATE INDEX IF NOT EXISTS idx_hub_parcel_status ON agency_org.hub_parcel_records (status);
CREATE INDEX IF NOT EXISTS idx_hub_parcel_core_entry ON agency_org.hub_parcel_records (core_hub_package_entry_id);

ALTER TABLE agency_org.hub_parcel_records ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_org.hub_parcel_records CASCADE;
