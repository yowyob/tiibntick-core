-- liquibase formatted sql
-- changeset manfouo-braun:007-add-spatial-gist-indexes
-- comment: Chantier G (Phase 1) — PostGIS GIST indexes for network_nodes/network_alerts/dao_zones
--   (Audit n6 S24, Audit n7 #24). Columns stay plain DOUBLE PRECISION lat/lng — same pattern as
--   tnt-geo-core's road_nodes (see logistics/tnt-geo-core/.../01_create_road_nodes.sql): a
--   functional GIST index on the geography expression, queried via ST_DWithin/ST_Intersects in
--   the R2DBC repositories, no geometry column migration needed.
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE INDEX IF NOT EXISTS idx_network_nodes_geography
    ON tnt_link.network_nodes
    USING GIST ((ST_MakePoint(longitude, latitude)::geography))
    WHERE longitude IS NOT NULL AND latitude IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_network_alerts_geography
    ON tnt_link.network_alerts
    USING GIST ((ST_MakePoint(longitude, latitude)::geography));

CREATE INDEX IF NOT EXISTS idx_dao_zones_center_geography
    ON tnt_link.dao_zones
    USING GIST ((ST_MakePoint(center_longitude, center_latitude)::geography));

-- The old non-spatial bbox pre-filter index is superseded by the GIST index above.
DROP INDEX IF EXISTS tnt_link.idx_network_nodes_bbox;
-- rollback DROP INDEX IF EXISTS tnt_link.idx_network_nodes_geography; DROP INDEX IF EXISTS tnt_link.idx_network_alerts_geography; DROP INDEX IF EXISTS tnt_link.idx_dao_zones_center_geography; CREATE INDEX IF NOT EXISTS idx_network_nodes_bbox ON tnt_link.network_nodes (tenant_id, latitude, longitude);
