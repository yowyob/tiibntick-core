-- =============================================================================
-- tnt-geo-core: road_nodes table
-- Schema: tnt_geography
-- Author: MANFOUO Braun
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS tnt_geography;

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS tnt_geography.road_nodes (
    id            VARCHAR(36)      NOT NULL,
    tenant_id     UUID             NOT NULL,
    type          VARCHAR(30)      NOT NULL,
    latitude      DOUBLE PRECISION NOT NULL,
    longitude     DOUBLE PRECISION NOT NULL,
    name          VARCHAR(255)     NOT NULL,
    city_code     VARCHAR(10)      NOT NULL,
    is_active     BOOLEAN          NOT NULL DEFAULT TRUE,
    capacity_slots INTEGER,
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    CONSTRAINT road_nodes_pk PRIMARY KEY (id),
    CONSTRAINT road_nodes_lat_range CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT road_nodes_lng_range CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT road_nodes_type_check CHECK (type IN ('CLIENT_POINT','RELAY_HUB','DEPOT','WAYPOINT'))
);

-- GiST index on geography for ST_DWithin spatial queries (sub-millisecond with PostGIS)
CREATE INDEX IF NOT EXISTS idx_road_nodes_geography
    ON tnt_geography.road_nodes
    USING GIST ((ST_MakePoint(longitude, latitude)::geography));

CREATE INDEX IF NOT EXISTS idx_road_nodes_tenant
    ON tnt_geography.road_nodes (tenant_id, is_active);

CREATE INDEX IF NOT EXISTS idx_road_nodes_city
    ON tnt_geography.road_nodes (tenant_id, city_code);
