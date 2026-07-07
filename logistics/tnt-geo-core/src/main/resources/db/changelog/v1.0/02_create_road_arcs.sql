-- =============================================================================
-- tnt-geo-core: road_arcs table
-- Schema: tnt_geography
-- Author: MANFOUO Braun
-- =============================================================================

CREATE TABLE IF NOT EXISTS tnt_geography.road_arcs (
    id              VARCHAR(36)      NOT NULL,
    tenant_id       UUID             NOT NULL,
    source_id       VARCHAR(36)      NOT NULL,
    target_id       VARCHAR(36)      NOT NULL,
    distance_km     DOUBLE PRECISION NOT NULL,
    road_type       VARCHAR(20)      NOT NULL,
    base_speed_kmh  DOUBLE PRECISION NOT NULL DEFAULT 50.0,
    traffic_factor  DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    is_bidirectional BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    CONSTRAINT road_arcs_pk           PRIMARY KEY (id),
    CONSTRAINT road_arcs_source_fk    FOREIGN KEY (source_id) REFERENCES tnt_geography.road_nodes(id) ON DELETE CASCADE,
    CONSTRAINT road_arcs_target_fk    FOREIGN KEY (target_id) REFERENCES tnt_geography.road_nodes(id) ON DELETE CASCADE,
    CONSTRAINT road_arcs_distance_pos CHECK (distance_km > 0),
    CONSTRAINT road_arcs_speed_pos    CHECK (base_speed_kmh > 0),
    CONSTRAINT road_arcs_traffic_pos  CHECK (traffic_factor > 0),
    CONSTRAINT road_arcs_type_check   CHECK (road_type IN ('HIGHWAY','PAVED','DEGRADED','DIRT'))
);

CREATE INDEX IF NOT EXISTS idx_road_arcs_tenant
    ON tnt_geography.road_arcs (tenant_id);

CREATE INDEX IF NOT EXISTS idx_road_arcs_source
    ON tnt_geography.road_arcs (source_id, tenant_id);

CREATE INDEX IF NOT EXISTS idx_road_arcs_target
    ON tnt_geography.road_arcs (target_id, tenant_id);
