-- tnt-route-core: tours table
-- Author: MANFOUO Braun
CREATE TABLE IF NOT EXISTS tnt_route.tours (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    deliverer_id      VARCHAR(36)  NOT NULL,
    total_cost        DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    total_distance_km DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    planning_date     DATE         NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    stops_json        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT tours_pk PRIMARY KEY (id),
    CONSTRAINT tours_status_check CHECK (status IN ('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_tours_tenant_date ON tnt_route.tours (tenant_id, planning_date);
CREATE INDEX IF NOT EXISTS idx_tours_deliverer ON tnt_route.tours (deliverer_id, tenant_id, planning_date);
