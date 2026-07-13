--liquibase formatted sql
--changeset jeff-belekotan:002_create_agency_missions
--comment: Agency-side mission projection — operational detail lives in tnt-delivery-core

CREATE TABLE IF NOT EXISTS agency_assignment.agency_missions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    agency_id               UUID NOT NULL,
    core_mission_id         UUID NOT NULL,
    assigned_deliverer_id   UUID,
    assigned_vehicle_id     UUID,
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    scheduled_at            TIMESTAMPTZ,
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    cancellation_reason     TEXT,
    quoted_amount           NUMERIC(18, 2),
    quoted_currency         VARCHAR(10),
    branch_id               UUID,
    pickup_address          TEXT,
    delivery_address        TEXT,
    sender_name             VARCHAR(255),
    recipient_name          VARCHAR(255),
    recipient_phone         VARCHAR(50),
    weight_kg               DOUBLE PRECISION,
    distance_km             DOUBLE PRECISION,
    packages_count          INTEGER,
    priority                VARCHAR(20),
    target_hub_id           UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_agency_missions_core_tenant UNIQUE (core_mission_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_agency_missions_tenant ON agency_assignment.agency_missions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agency_missions_agency ON agency_assignment.agency_missions (agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_missions_status ON agency_assignment.agency_missions (status);
CREATE INDEX IF NOT EXISTS idx_agency_missions_deliverer ON agency_assignment.agency_missions (assigned_deliverer_id);
CREATE INDEX IF NOT EXISTS idx_agency_missions_core ON agency_assignment.agency_missions (core_mission_id);

ALTER TABLE agency_assignment.agency_missions ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_assignment.agency_missions CASCADE;
