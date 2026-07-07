-- liquibase formatted sql
-- Author: MANFOUO Braun
-- Module: tnt-organization-core (L2 Identity) — 
-- Description: Creates the tnt_freelancer_operational_zone table.
--              Stores polygonal service coverage zones for FreelancerOrganizations.
--              Zones are stored as WKT TEXT compatible with PostGIS for spatial queries.

-- changeset manfouo-braun:v0006-create-freelancer-zones dbms:postgresql splitStatements:true endDelimiter:;
CREATE TABLE IF NOT EXISTS tnt_freelancer_operational_zone
(
    -- Synthetic primary key
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- FK to the owning FreelancerOrganization
    freelancer_org_id   UUID            NOT NULL,

    -- Human-readable zone name (e.g. "Quartier Bastos Yaoundé")
    zone_name           VARCHAR(255)    NOT NULL,

    -- WKT POLYGON defining the zone boundary (SRID 4326).
    -- PostGIS compatible: use ST_GeomFromText(polygon_wkt, 4326) for spatial operations.
    -- Example: POLYGON((3.85 11.5, 3.86 11.5, 3.86 11.51, 3.85 11.51, 3.85 11.5))
    polygon_wkt         TEXT            NOT NULL,

    -- Whether this zone is currently active and accepting deliveries
    active              BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Road/access difficulty (ZoneAccessDifficulty enum name: LOW, MEDIUM, HIGH, VERY_HIGH)
    access_difficulty   VARCHAR(20)     NOT NULL DEFAULT 'LOW',

    -- Zone urbanisation type (DeliveryZoneType enum name: URBAN, PERI_URBAN, RURAL, ...)
    zone_type           VARCHAR(20)     NOT NULL DEFAULT 'URBAN',

    CONSTRAINT pk_tnt_freelancer_zone PRIMARY KEY (id),

    CONSTRAINT fk_freelancer_zone_org
        FOREIGN KEY (freelancer_org_id)
        REFERENCES tnt_freelancer_organization (id)
        ON DELETE CASCADE
);

-- Index for loading all zones of an org (most common query)
-- changeset manfouo-braun:v0006-idx-zone-org-id dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_freelancer_zone_org_id
    ON tnt_freelancer_operational_zone (freelancer_org_id);

-- Index for filtering active zones only
-- changeset manfouo-braun:v0006-idx-zone-active dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_freelancer_zone_active
    ON tnt_freelancer_operational_zone (freelancer_org_id, active);
