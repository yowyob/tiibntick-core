--liquibase formatted sql
-- Author: MANFOUO Braun

--changeset tnt-resource:007 labels:resource,freelancer-fleet
-- comment: Creates tables for FreelancerOrganization fleet management (tnt-resource-core )
-- FreelancerVehicle and FreelancerEquipment are TNT-exclusive entities owned by FreelancerOrg.
-- freelancer_org_id is a logical UUID reference to tnt-organization-core.freelancer_organizations
-- (no physical FK across module boundaries — cross-database logical reference).

-- ════════════════════════════════════════════════════════════════════════════
-- TABLE: tnt_freelancer_vehicles
-- Personal fleet of a FreelancerOrganization (1 to 3 vehicles per org).
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS tnt_freelancer_vehicles (
    id                              UUID             NOT NULL PRIMARY KEY,

    -- Logical reference to tnt-organization-core.freelancer_organizations(id).
    -- No physical FK — cross-module boundary. Validated via event-driven consistency.
    freelancer_org_id               UUID             NOT NULL,

    -- Vehicle type: MOTO | VELO | VOITURE | CAMIONNETTE | VELO_CARGO | (legacy Agency types)
    vehicle_type                    VARCHAR(50)      NOT NULL,

    brand                           VARCHAR(100),
    model                           VARCHAR(100),
    plate_number                    VARCHAR(50)      NOT NULL,
    max_capacity_kg                 DECIMAL(8,2)     NOT NULL,
    volume_m3                       DECIMAL(6,3),

    -- Fuel type: ESSENCE | DIESEL | ELECTRIQUE | HYBRIDE
    fuel_type                       VARCHAR(30),

    -- Fuel consumption in liters per 100km (used by tnt-billing-cost FleetCostParameters)
    fuel_consumption_l_per_100km    DECIMAL(5,2),

    -- References to documents in tnt-media-core (stored as URI strings)
    registration_doc_ref            VARCHAR(500),
    insurance_doc_ref               VARCHAR(500),

    is_active                       BOOLEAN          NOT NULL DEFAULT TRUE,
    last_maintenance_at             DATE,

    -- Mission assignment tracking (null = available)
    current_mission_id              VARCHAR(255),

    created_at                      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_tnt_freelancer_vehicles_plate UNIQUE (plate_number)
);

COMMENT ON COLUMN tnt_freelancer_vehicles.freelancer_org_id
    IS 'Logical reference to tnt-organization-core.freelancer_organizations(id). '
       'No physical FK — cross-module boundary. Validated via event-driven consistency.';

COMMENT ON COLUMN tnt_freelancer_vehicles.fuel_consumption_l_per_100km
    IS 'Fuel consumption in liters per 100 km. Used by tnt-billing-cost FleetCostParameters '
       'to compute mission operational cost.';

-- Index for fleet listing per org (most common query)
CREATE INDEX IF NOT EXISTS idx_tnt_fl_vehicles_org
    ON tnt_freelancer_vehicles (freelancer_org_id);

-- Index for available vehicle matching (active + no current mission)
CREATE INDEX IF NOT EXISTS idx_tnt_fl_vehicles_available
    ON tnt_freelancer_vehicles (freelancer_org_id)
    WHERE is_active = TRUE AND current_mission_id IS NULL;

-- Partial index for capacity-based vehicle matching
CREATE INDEX IF NOT EXISTS idx_tnt_fl_vehicles_capacity
    ON tnt_freelancer_vehicles (freelancer_org_id, max_capacity_kg)
    WHERE is_active = TRUE AND current_mission_id IS NULL;

-- ════════════════════════════════════════════════════════════════════════════
-- TABLE: tnt_freelancer_equipments
-- Specialized physical equipment owned by a FreelancerOrganization.
-- ════════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS tnt_freelancer_equipments (
    id                              UUID             NOT NULL PRIMARY KEY,

    -- Logical reference to tnt-organization-core.freelancer_organizations(id)
    freelancer_org_id               UUID             NOT NULL,

    -- Equipment type: REFRIGERATED_BOX | CARGO_BAG | WATERPROOF_COVER | TRACKING_BEACON |
    --                 PADLOCK | PARCEL_SCANNER | THERMAL_BAG | FRAGILE_FOAM | OVERSIZED_RACK |
    --                 (legacy: QR_SCANNER | TABLET | PAYMENT_TERMINAL | GPS_TRACKER | etc.)
    equipment_type                  VARCHAR(100)     NOT NULL,

    description                     TEXT,
    max_capacity_kg                 DECIMAL(8,2),

    -- Ownership type: OWNED | RENTED
    ownership_type                  VARCHAR(20)      NOT NULL DEFAULT 'OWNED',

    is_active                       BOOLEAN          NOT NULL DEFAULT TRUE,

    -- Mission assignment tracking (null = available)
    currently_assigned_mission_id   VARCHAR(255),

    created_at                      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN tnt_freelancer_equipments.freelancer_org_id
    IS 'Logical reference to tnt-organization-core.freelancer_organizations(id). '
       'No physical FK — cross-module boundary.';

COMMENT ON COLUMN tnt_freelancer_equipments.equipment_type
    IS 'Specialized equipment type for FreelancerOrg deliveries. '
       'REFRIGERATED_BOX enables hasRefrigeratedBox DSL variable for billing.';

-- Index for equipment listing per org
CREATE INDEX IF NOT EXISTS idx_tnt_fl_equipments_org
    ON tnt_freelancer_equipments (freelancer_org_id);

-- Partial index for active equipment type lookup (DSL variable resolution)
CREATE INDEX IF NOT EXISTS idx_tnt_fl_equipments_type_active
    ON tnt_freelancer_equipments (freelancer_org_id, equipment_type)
    WHERE is_active = TRUE;

-- Partial index for refrigerated box check (hasRefrigeratedBox DSL variable)
CREATE INDEX IF NOT EXISTS idx_tnt_fl_equipments_refrigerated
    ON tnt_freelancer_equipments (freelancer_org_id)
    WHERE equipment_type = 'REFRIGERATED_BOX' AND is_active = TRUE;

-- rollback DROP TABLE IF EXISTS tnt_freelancer_equipments; DROP TABLE IF EXISTS tnt_freelancer_vehicles;
