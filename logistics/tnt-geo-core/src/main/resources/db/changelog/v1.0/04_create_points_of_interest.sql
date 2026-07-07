-- =============================================================================
-- tnt-geo-core: points_of_interest table
-- Local African landmarks used as geocoding anchors
-- Author: MANFOUO Braun
-- =============================================================================

CREATE TABLE IF NOT EXISTS tnt_geography.points_of_interest (
    id          UUID             NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID,
    name        VARCHAR(255)     NOT NULL,
    type        VARCHAR(50)      NOT NULL,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    description TEXT,
    city_code   VARCHAR(10)      NOT NULL,
    is_verified BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    CONSTRAINT poi_pk          PRIMARY KEY (id),
    CONSTRAINT poi_lat_range   CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT poi_lng_range   CHECK (longitude BETWEEN -180 AND 180)
);

-- Spatial index for nearby POI searches
CREATE INDEX IF NOT EXISTS idx_poi_geography
    ON tnt_geography.points_of_interest
    USING GIST ((ST_MakePoint(longitude, latitude)::geography));

CREATE INDEX IF NOT EXISTS idx_poi_city
    ON tnt_geography.points_of_interest (city_code, is_verified);

CREATE INDEX IF NOT EXISTS idx_poi_tenant
    ON tnt_geography.points_of_interest (tenant_id);
