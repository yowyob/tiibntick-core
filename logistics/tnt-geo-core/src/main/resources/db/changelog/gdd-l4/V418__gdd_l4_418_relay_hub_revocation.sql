-- =============================================================================
-- Go-Freelancer de-duplication, correction after lots L3/L4 (gdd-l4-418), maintainer decision D-129 (2026-11-26):
-- a relay hub can be REVOKED for good by an administrator, with a mandatory reason.
-- Additive and re-runnable: three nullable columns; the status check constraint is re-created with REVOKED
-- (dropping and re-creating a CHECK constraint changes no data); a REVOKED row must carry its reason and date.
-- =============================================================================

ALTER TABLE tnt_geography.relay_hubs ADD COLUMN IF NOT EXISTS revocation_reason   TEXT;
ALTER TABLE tnt_geography.relay_hubs ADD COLUMN IF NOT EXISTS revoked_at          TIMESTAMPTZ;
ALTER TABLE tnt_geography.relay_hubs ADD COLUMN IF NOT EXISTS revoked_by_actor_id UUID;

ALTER TABLE tnt_geography.relay_hubs DROP CONSTRAINT IF EXISTS relay_hubs_status_check;
ALTER TABLE tnt_geography.relay_hubs ADD CONSTRAINT relay_hubs_status_check
    CHECK (status IN ('ACTIVE','FULL','TEMPORARILY_CLOSED','PERMANENTLY_CLOSED','REVOKED'));

ALTER TABLE tnt_geography.relay_hubs DROP CONSTRAINT IF EXISTS relay_hubs_revocation_complete;
ALTER TABLE tnt_geography.relay_hubs ADD CONSTRAINT relay_hubs_revocation_complete
    CHECK (status <> 'REVOKED' OR (revocation_reason IS NOT NULL AND btrim(revocation_reason) <> ''
                                   AND revoked_at IS NOT NULL));
