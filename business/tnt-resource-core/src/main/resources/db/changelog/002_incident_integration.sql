--liquibase formatted sql
-- Author: MANFOUO Braun
-- changeset tnt-resource:002 labels:resource,incident-integration
-- comment: Adds has_refrigeration column and IN_INCIDENT_SUBSTITUTION status support for tnt-incident-core

-- ════════════════════════════════════════════════════════════════════════════
-- TABLE: tnt_vehicles
-- Add has_refrigeration column for IVehicleCompatibilityPort.getVehicleInfo()
-- The IN_INCIDENT_SUBSTITUTION status is stored in the existing 'status' VARCHAR(30)
-- column — no migration needed for the new status value itself.
-- ════════════════════════════════════════════════════════════════════════════

ALTER TABLE tnt_vehicles
    ADD COLUMN IF NOT EXISTS has_refrigeration BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN tnt_vehicles.has_refrigeration
    IS 'Indicates whether this vehicle has a cold chain/refrigeration system. '
       'Used by IVehicleCompatibilityPort (tnt-incident-core) to match vehicles '
       'with temperature-sensitive parcel delivery requirements.';

-- Partial index for refrigerated vehicles (rare — only a few per fleet)
CREATE INDEX IF NOT EXISTS idx_tnt_vehicles_refrigerated
    ON tnt_vehicles (tenant_id, agency_id)
    WHERE has_refrigeration = TRUE AND status = 'AVAILABLE';

-- Index for IN_INCIDENT_SUBSTITUTION status (needed by incident auto-resolution)
CREATE INDEX IF NOT EXISTS idx_tnt_vehicles_incident_substitution
    ON tnt_vehicles (tenant_id, status)
    WHERE status = 'IN_INCIDENT_SUBSTITUTION';

-- Composite index for the findAvailableNear query (used by IDriverAvailabilityPort)
-- Covers: tenant_id + status='AVAILABLE' + max_weight_kg filter
CREATE INDEX IF NOT EXISTS idx_tnt_vehicles_available_capacity
    ON tnt_vehicles (tenant_id, agency_id, status, max_weight_kg)
    WHERE status = 'AVAILABLE' AND gps_latitude IS NOT NULL;

-- rollback ALTER TABLE tnt_vehicles DROP COLUMN IF EXISTS has_refrigeration;
