-- =============================================================================
-- Go-Freelancer de-duplication, correction after lots L3/L4 (gdd-l4-422), maintainer decision D-138 (2026-11-26):
-- the approval of a relay hub lives in geo-core (gdd-l4-421) and the Go relay point copies it from now on. For the
-- hubs that already back a Go relay point, the initial approval is the relay point's status, nothing invented:
--   * a hub linked to relay points that all carry the same status takes that status (PENDING, APPROVED, SUSPENDED,
--     REJECTED or REVOKED, the same five values); a REVOKED one gets its revocation date (the relay point's last
--     update when the hub has none) — its reason stays empty when the relay point was revoked before D-129;
--   * a hub linked to relay points with DIFFERENT statuses is ambiguous: counted (NOTICE) and left as gdd-l4-421 set
--     it (APPROVED, as before D-138). gofp_relay_points.core_relay_point_id is UNIQUE, so this is a safety net;
--   * a relay point with a status outside the five values is ambiguous too: counted, its hub left as it is;
--   * relay points whose core_relay_point_id matches no hub are counted (NOTICE): no hub to align.
-- The operating status of the hubs is not touched (separate, D-138). Re-runnable: the copy is idempotent.
-- =============================================================================
DO $$
DECLARE
    n_aligned   BIGINT := 0;
    n_ambiguous BIGINT := 0;
    n_no_hub    BIGINT := 0;
    n_unknown   BIGINT := 0;
BEGIN
    IF to_regclass('tnt_geography.relay_hubs') IS NULL OR to_regclass('gofp_relay_points') IS NULL THEN
        RAISE NOTICE 'gdd-l4-422 (D-138): relay_hubs or gofp_relay_points missing, nothing to align';
        RETURN;
    END IF;

    CREATE TEMP TABLE gdd_l4_422_link ON COMMIT DROP AS
    SELECT rp.core_relay_point_id AS hub_id,
           min(rp.status)          AS status,
           count(DISTINCT rp.status) AS statuses,
           max(rp.updated_at)      AS last_update
      FROM gofp_relay_points rp
      JOIN tnt_geography.relay_hubs h ON h.id = rp.core_relay_point_id
     WHERE rp.status IN ('PENDING','APPROVED','SUSPENDED','REJECTED','REVOKED')
     GROUP BY rp.core_relay_point_id;

    SELECT count(*) INTO n_ambiguous FROM gdd_l4_422_link WHERE statuses > 1;
    SELECT count(*) INTO n_unknown
      FROM gofp_relay_points rp
      JOIN tnt_geography.relay_hubs h ON h.id = rp.core_relay_point_id
     WHERE rp.status IS NULL OR rp.status NOT IN ('PENDING','APPROVED','SUSPENDED','REJECTED','REVOKED');

    UPDATE tnt_geography.relay_hubs h
       SET approval_status = l.status,
           revoked_at = CASE WHEN l.status = 'REVOKED' THEN COALESCE(h.revoked_at, l.last_update, now())
                             ELSE h.revoked_at END,
           updated_at = now()
      FROM gdd_l4_422_link l
     WHERE h.id = l.hub_id AND l.statuses = 1 AND h.approval_status IS DISTINCT FROM l.status;
    GET DIAGNOSTICS n_aligned = ROW_COUNT;

    SELECT count(*) INTO n_no_hub
      FROM gofp_relay_points rp
     WHERE rp.core_relay_point_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM tnt_geography.relay_hubs h WHERE h.id = rp.core_relay_point_id);

    RAISE NOTICE 'gdd-l4-422 (D-138) relay hub approval copied from the Go relay points: % hub(s) aligned, % ambiguous '
        'hub(s) (relay points with different statuses, left as they were), % relay point(s) with an unknown status '
        '(hub left as it was), % relay point(s) without a hub', n_aligned, n_ambiguous, n_unknown, n_no_hub;
END $$;
