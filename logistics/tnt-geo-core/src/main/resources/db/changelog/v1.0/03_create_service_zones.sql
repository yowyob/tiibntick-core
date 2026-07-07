-- =============================================================================
-- tnt-geo-core: service_zones table
-- Vertices stored as JSONB for R2DBC compatibility (PostGIS POLYGON requires custom codec)
-- Author: MANFOUO Braun
-- =============================================================================

CREATE TABLE IF NOT EXISTS tnt_geography.service_zones (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    agency_id     UUID         NOT NULL,
    name          VARCHAR(255) NOT NULL,
    vertices_json JSONB        NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT service_zones_pk      PRIMARY KEY (id),
    CONSTRAINT service_zones_name_nn CHECK (char_length(name) > 0)
);

CREATE INDEX IF NOT EXISTS idx_service_zones_tenant
    ON tnt_geography.service_zones (tenant_id, is_active);

CREATE INDEX IF NOT EXISTS idx_service_zones_agency
    ON tnt_geography.service_zones (agency_id, tenant_id);
