--liquibase formatted sql
-- Author: MANFOUO Braun
-- Module: tnt-billing-cost — Fleet Cost Parameters ()

--changeset tnt-billing-cost:001 labels:billing-cost,fleet-params
-- comment: Creates fleet_cost_parameters table for per-actor fleet cost calibration
-- FreelancerOrg and Agency can have custom cost parameters overriding global tenant settings.

CREATE TABLE IF NOT EXISTS fleet_cost_parameters (
    id                          UUID             NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),

    -- UUID of the owning FreelancerOrg or Agency
    -- References tnt-organization-core UUID — pure integration key (no physical FK)
    owner_org_id                VARCHAR(255)     NOT NULL UNIQUE,

    -- Fuel price per liter in XAF (overrides global tenant setting)
    fuel_price_liter_xaf        DECIMAL(10,2)    NOT NULL DEFAULT 700,

    -- Vehicle wear rate per km in XAF/km
    vehicle_wear_rate_per_km    DECIMAL(10,4)    NOT NULL DEFAULT 10,

    -- Value of deliverer's time per hour in XAF/h
    time_value_per_hour         DECIMAL(10,2)    NOT NULL DEFAULT 500,

    -- Terrain degradation factor [1.0, 2.0] — amplifies penibility cost
    terrain_degradation_factor  DECIMAL(4,2)     NOT NULL DEFAULT 1.0,

    -- Rain penalty factor [1.0, 1.5] — amplifies weather surcharge
    rain_penalty_factor         DECIMAL(4,2)     NOT NULL DEFAULT 1.1,

    -- Whether to auto-update fuel price from national reference API
    auto_update_fuel_price      BOOLEAN          NOT NULL DEFAULT FALSE,

    -- Timestamp of last auto-update (null if never)
    last_fuel_price_update_at   TIMESTAMP,

    updated_at                  TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN fleet_cost_parameters.owner_org_id
    IS 'UUID of the owning FreelancerOrg or Agency. '
       'References tnt-organization-core UUID — no physical FK (cross-module).';

COMMENT ON COLUMN fleet_cost_parameters.terrain_degradation_factor
    IS 'Multiplier for penibility cost based on typical terrain of the actor''s area. '
       'Range [1.0, 2.0]. 1.0 = good roads, 2.0 = very degraded roads.';

COMMENT ON COLUMN fleet_cost_parameters.rain_penalty_factor
    IS 'Multiplier applied to weather surcharge during rain. '
       'Range [1.0, 1.5]. Reflects real impact of rain on this actor''s fleet.';

-- Index for fast per-org lookup
CREATE INDEX IF NOT EXISTS idx_fleet_cost_params_org
    ON fleet_cost_parameters (owner_org_id);

-- rollback DROP TABLE IF EXISTS fleet_cost_parameters;
