-- =============================================================================
-- Go-Freelancer de-duplication, correction after lots L3/L4 (gdd-l4-421), maintainer decision D-138 (2026-11-26):
-- a relay hub carries an APPROVAL status (PENDING, APPROVED, SUSPENDED, REJECTED, REVOKED), separate from its
-- operating status (ACTIVE, FULL, TEMPORARILY_CLOSED, PERMANENTLY_CLOSED). The approval is authoritative for the
-- Go-Freelancer relay points (Agency has no relay point: D-143); the revocation (D-129, D-135) moves from the
-- operating status to the approval.
--
-- Initial approval of the existing hubs, nothing invented:
--   * a hub whose operating status was REVOKED (gdd-l4-418, never published outside this branch) -> approval
--     REVOKED, operating status PERMANENTLY_CLOSED (a revoked hub never operates again);
--   * every other existing hub operated without any approval step -> APPROVED. Hubs whose operating status is
--     closed are counted (NOTICE): they stay approved, their closure is an operating state (D-138).
--   * hubs backing a Go-Freelancer relay point are then aligned on the relay point's status by the Go changeset
--     gdd-l4-422 (geo-core does not read Go tables).
-- A new hub starts PENDING (column default, and RelayHub.create).
-- Re-runnable: every step is guarded.
-- =============================================================================
DO $$
DECLARE
    n_revoked BIGINT := 0;
    n_approved BIGINT := 0;
    n_closed  BIGINT := 0;
BEGIN
    ALTER TABLE tnt_geography.relay_hubs ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20);

    -- the operating status no longer carries REVOKED
    ALTER TABLE tnt_geography.relay_hubs DROP CONSTRAINT IF EXISTS relay_hubs_revocation_complete;
    ALTER TABLE tnt_geography.relay_hubs DROP CONSTRAINT IF EXISTS relay_hubs_status_check;

    UPDATE tnt_geography.relay_hubs
       SET approval_status = 'REVOKED', status = 'PERMANENTLY_CLOSED',
           revoked_at = COALESCE(revoked_at, updated_at, now())
     WHERE status = 'REVOKED';
    GET DIAGNOSTICS n_revoked = ROW_COUNT;

    SELECT count(*) INTO n_closed FROM tnt_geography.relay_hubs
     WHERE approval_status IS NULL AND status IN ('TEMPORARILY_CLOSED', 'PERMANENTLY_CLOSED');
    UPDATE tnt_geography.relay_hubs SET approval_status = 'APPROVED' WHERE approval_status IS NULL;
    GET DIAGNOSTICS n_approved = ROW_COUNT;

    ALTER TABLE tnt_geography.relay_hubs ALTER COLUMN approval_status SET DEFAULT 'PENDING';
    ALTER TABLE tnt_geography.relay_hubs ALTER COLUMN approval_status SET NOT NULL;
    ALTER TABLE tnt_geography.relay_hubs ADD CONSTRAINT relay_hubs_status_check
        CHECK (status IN ('ACTIVE','FULL','TEMPORARILY_CLOSED','PERMANENTLY_CLOSED'));
    ALTER TABLE tnt_geography.relay_hubs DROP CONSTRAINT IF EXISTS relay_hubs_approval_status_check;
    ALTER TABLE tnt_geography.relay_hubs ADD CONSTRAINT relay_hubs_approval_status_check
        CHECK (approval_status IN ('PENDING','APPROVED','SUSPENDED','REJECTED','REVOKED'));
    -- a revoked hub carries its revocation date; the reason is mandatory for every new revocation (domain), it can
    -- be missing on a relay point revoked before D-129 (gdd-l4-422)
    ALTER TABLE tnt_geography.relay_hubs DROP CONSTRAINT IF EXISTS relay_hubs_approval_revocation_dated;
    ALTER TABLE tnt_geography.relay_hubs ADD CONSTRAINT relay_hubs_approval_revocation_dated
        CHECK (approval_status <> 'REVOKED' OR revoked_at IS NOT NULL);
    CREATE INDEX IF NOT EXISTS idx_relay_hubs_tenant_approval
        ON tnt_geography.relay_hubs (tenant_id, approval_status, status);

    RAISE NOTICE 'gdd-l4-421 (D-138) relay hub approval: % revoked hub(s) moved to approval REVOKED, % existing hub(s) '
        'APPROVED (of which % with a closed operating status, kept closed)', n_revoked, n_approved, n_closed;
END $$;
