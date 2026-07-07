--liquibase formatted sql
-- Author: MANFOUO Braun

--changeset tnt-resource:001 labels:resource,schema
-- tnt_vehicles: mapped to VehicleEntity column names.
-- assignedDelivererId is an actorId UUID → logical reference to RT-comops-actor-core.
-- agencyId is an organizationId UUID → logical reference to RT-comops-organization-core.
-- No kernelVehicleId: Vehicle is TNT-exclusive (no Kernel counterpart).
CREATE TABLE IF NOT EXISTS tnt_vehicles (
    id                                  UUID             NOT NULL PRIMARY KEY,
    tenant_id                           UUID             NOT NULL,
    organization_id                     UUID             NOT NULL,
    agency_id                           UUID             NOT NULL,
    registration_number                 VARCHAR(50)      NOT NULL,
    brand                               VARCHAR(100)     NOT NULL,
    model                               VARCHAR(100)     NOT NULL,
    year_of_manufacture                 INTEGER          NOT NULL,
    type                                VARCHAR(30)      NOT NULL,
    max_weight_kg                       DOUBLE PRECISION NOT NULL,
    max_volume_m3                       DOUBLE PRECISION NOT NULL,
    status                              VARCHAR(30)      NOT NULL DEFAULT 'AVAILABLE',
    -- Logical reference to RT-comops-actor-core actor UUID (deliverer actorId).
    -- No physical FK cross-database. Validated best-effort via KernelActorPort.
    assigned_deliverer_id               UUID,
    odometer_km                         DOUBLE PRECISION NOT NULL DEFAULT 0,
    gps_latitude                        DOUBLE PRECISION,
    gps_longitude                       DOUBLE PRECISION,
    last_location_update                TIMESTAMPTZ,
    next_maintenance_date               DATE,
    next_maintenance_type               VARCHAR(20),
    next_maintenance_reason             TEXT,
    next_maintenance_odometer_threshold DOUBLE PRECISION,
    created_at                          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tnt_vehicles_tenant_reg UNIQUE (tenant_id, registration_number)
);

-- Index on tenant + agency for fleet listing (guide: ensure agency_id is indexed)
CREATE INDEX IF NOT EXISTS idx_tnt_vehicles_tenant_agency
    ON tnt_vehicles (tenant_id, agency_id);

-- Index on status for vehicle matching queries
CREATE INDEX IF NOT EXISTS idx_tnt_vehicles_tenant_status
    ON tnt_vehicles (tenant_id, status);

-- Partial index on deliverer for assignment lookup (guide: ensure assigned_deliverer_id is indexed)
CREATE INDEX IF NOT EXISTS idx_tnt_vehicles_deliverer
    ON tnt_vehicles (assigned_deliverer_id)
    WHERE assigned_deliverer_id IS NOT NULL;

-- Index on organization for organizational lookups
CREATE INDEX IF NOT EXISTS idx_tnt_vehicles_org
    ON tnt_vehicles (tenant_id, organization_id);

COMMENT ON COLUMN tnt_vehicles.assigned_deliverer_id
    IS 'Logical reference to RT-comops-actor-core actor UUID (deliverer actorId). '
       'No physical FK — cross-database logical reference validated via KernelActorPort.';

COMMENT ON COLUMN tnt_vehicles.agency_id
    IS 'Logical reference to RT-comops-organization-core organization UUID. '
       'Represents the agency (branch) that manages this vehicle.';

--changeset tnt-resource:002 labels:resource,schema
-- tnt_vehicle_maintenance_records: mapped to VehicleMaintenanceRecordEntity column names.
CREATE TABLE IF NOT EXISTS tnt_vehicle_maintenance_records (
    id               UUID             NOT NULL PRIMARY KEY,
    vehicle_id       UUID             NOT NULL REFERENCES tnt_vehicles(id) ON DELETE CASCADE,
    tenant_id        UUID             NOT NULL,
    agency_id        UUID             NOT NULL,
    type             VARCHAR(20)      NOT NULL,
    description      TEXT             NOT NULL,
    odometer_km      DOUBLE PRECISION NOT NULL DEFAULT 0,
    scheduled_date   DATE,
    completed_date   DATE,
    technician_name  VARCHAR(255),
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tnt_maint_tenant_vehicle
    ON tnt_vehicle_maintenance_records (tenant_id, vehicle_id);

CREATE INDEX IF NOT EXISTS idx_tnt_maint_pending
    ON tnt_vehicle_maintenance_records (vehicle_id, completed_date)
    WHERE completed_date IS NULL;

--changeset tnt-resource:003 labels:resource,schema
-- tnt_equipment: mapped to EquipmentEntity column names.
CREATE TABLE IF NOT EXISTS tnt_equipment (
    id               UUID         NOT NULL PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    organization_id  UUID         NOT NULL,
    branch_id        UUID         NOT NULL,
    type             VARCHAR(30)  NOT NULL,
    serial_number    VARCHAR(100) NOT NULL,
    description      TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    assigned_user_id UUID,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tnt_equipment_tenant_serial UNIQUE (tenant_id, serial_number)
);

CREATE INDEX IF NOT EXISTS idx_tnt_equipment_tenant
    ON tnt_equipment (tenant_id);

CREATE INDEX IF NOT EXISTS idx_tnt_equipment_branch
    ON tnt_equipment (tenant_id, branch_id);

CREATE INDEX IF NOT EXISTS idx_tnt_equipment_status
    ON tnt_equipment (tenant_id, status);

-- Partial index on assignedUserId for user-level equipment lookup
CREATE INDEX IF NOT EXISTS idx_tnt_equipment_user
    ON tnt_equipment (assigned_user_id)
    WHERE assigned_user_id IS NOT NULL;

--changeset tnt-resource:004 labels:resource,schema
-- tnt_resource_allocations: mapped to ResourceAllocationEntity column names.
CREATE TABLE IF NOT EXISTS tnt_resource_allocations (
    id                   UUID         NOT NULL PRIMARY KEY,
    tenant_id            UUID         NOT NULL,
    agency_id            UUID         NOT NULL,
    resource_id          UUID         NOT NULL,
    resource_type        VARCHAR(20)  NOT NULL,
    assigned_to_user_id  UUID         NOT NULL,
    mission_id           UUID,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    allocated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    released_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tnt_alloc_resource
    ON tnt_resource_allocations (tenant_id, resource_id);

CREATE INDEX IF NOT EXISTS idx_tnt_alloc_user
    ON tnt_resource_allocations (tenant_id, assigned_to_user_id);

-- Partial index for active allocations lookup
CREATE INDEX IF NOT EXISTS idx_tnt_alloc_active
    ON tnt_resource_allocations (tenant_id, resource_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_tnt_alloc_mission
    ON tnt_resource_allocations (mission_id)
    WHERE mission_id IS NOT NULL;
