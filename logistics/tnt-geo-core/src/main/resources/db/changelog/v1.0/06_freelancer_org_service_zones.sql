--liquibase formatted sql
--changeset MANFOUO_Braun:006_freelancer_org_service_zones
--comment: Adds FreelancerOrg ownership columns to tnt_geography.service_zones table ().
-- A service zone can now be owned by a FreelancerOrganization in addition to an Agency.
-- The owner_type column discriminates between AGENCY and FREELANCER_ORG ownership.

ALTER TABLE tnt_geography.service_zones
    ADD COLUMN IF NOT EXISTS freelancer_org_id  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS owner_type         VARCHAR(30)  NOT NULL DEFAULT 'AGENCY';

COMMENT ON COLUMN tnt_geography.service_zones.freelancer_org_id
    IS 'UUID of the owning FreelancerOrganization. References tnt-organization-core UUID — '
       'no physical FK (cross-module boundary). Null for AGENCY-owned zones.';

COMMENT ON COLUMN tnt_geography.service_zones.owner_type
    IS 'AGENCY or FREELANCER_ORG — discriminates zone ownership type.';

-- Make agency_id nullable (was NOT NULL for all zones, now only required for AGENCY type)
ALTER TABLE tnt_geography.service_zones
    ALTER COLUMN agency_id DROP NOT NULL;

-- Index for FreelancerOrg zone lookup (used by IFreelancerOrgGeoUseCase)
CREATE INDEX IF NOT EXISTS idx_geo_sz_freelancer_org
    ON tnt_geography.service_zones (freelancer_org_id, tenant_id)
    WHERE freelancer_org_id IS NOT NULL AND is_active = TRUE;

-- Index for all FreelancerOrg zones by tenant (used by findFreelancerOrgsInZone)
CREATE INDEX IF NOT EXISTS idx_geo_sz_owner_type
    ON tnt_geography.service_zones (tenant_id, owner_type)
    WHERE is_active = TRUE;

--rollback ALTER TABLE tnt_geography.service_zones DROP COLUMN IF EXISTS freelancer_org_id, DROP COLUMN IF EXISTS owner_type; ALTER TABLE tnt_geography.service_zones ALTER COLUMN agency_id SET NOT NULL;
