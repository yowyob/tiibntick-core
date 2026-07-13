--liquibase formatted sql
--changeset jeff-belekotan:003_create_agency_settings

CREATE TABLE IF NOT EXISTS agency_org.agency_settings (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    agency_id                   UUID NOT NULL UNIQUE REFERENCES agency_org.agencies(id) ON DELETE CASCADE,
    auto_assign_missions        BOOLEAN NOT NULL DEFAULT false,
    allow_freelancer_association BOOLEAN NOT NULL DEFAULT false,
    hub_retention_delay_hours   INTEGER NOT NULL DEFAULT 72,
    default_currency            VARCHAR(3) NOT NULL DEFAULT 'XAF',
    default_commission_rate     NUMERIC(5,2) NOT NULL DEFAULT 10.00,
    max_active_branches         INTEGER NOT NULL DEFAULT 10,
    timezone                    VARCHAR(50) NOT NULL DEFAULT 'Africa/Douala',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                     BIGINT NOT NULL DEFAULT 0
);

--rollback DROP TABLE IF EXISTS agency_org.agency_settings CASCADE;
