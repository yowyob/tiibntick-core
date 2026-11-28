-- Go-Freelancer de-duplication, lot L4 (gdd-l4-413): after gdd-l4-412, the occupancy of the Go-Freelancer relay
-- hubs (hubs of gofp_relay_points or of relay_deposits) is recomputed from their active inventory entries, with the
-- rule of RelayHub.updateOccupancy: occupancy = active entries, FULL when it equals the capacity, ACTIVE otherwise.
-- Closed hubs keep their status; a hub with more active entries than slots is left as is (NOTICE).
DO $$
DECLARE
    v_over BIGINT;
BEGIN
    IF to_regclass('public.tnt_hub_package_entries') IS NULL OR to_regclass('tnt_geography.relay_hubs') IS NULL THEN
        RETURN;
    END IF;

    DROP TABLE IF EXISTS gdd_l4_413_hubs;
    CREATE TEMP TABLE gdd_l4_413_hubs ON COMMIT DROP AS
    SELECT h.id, h.capacity_slots,
           (SELECT count(*) FROM tnt_hub_package_entries e WHERE e.hub_id = h.id AND e.picked_up_at IS NULL) AS active
      FROM tnt_geography.relay_hubs h
     WHERE h.id IN (SELECT rp.core_relay_point_id FROM gofp_relay_points rp WHERE rp.core_relay_point_id IS NOT NULL)
        OR h.id IN (SELECT rd.logistics_id FROM relay_deposits rd);

    UPDATE tnt_geography.relay_hubs h
       SET current_occupancy = t.active,
           status            = CASE WHEN h.status IN ('ACTIVE', 'FULL')
                                    THEN CASE WHEN t.active = h.capacity_slots THEN 'FULL' ELSE 'ACTIVE' END
                                    ELSE h.status END,
           updated_at        = now()
      FROM gdd_l4_413_hubs t
     WHERE h.id = t.id AND t.active <= t.capacity_slots
       AND (h.current_occupancy <> t.active
            OR (h.status IN ('ACTIVE', 'FULL')
                AND h.status <> CASE WHEN t.active = h.capacity_slots THEN 'FULL' ELSE 'ACTIVE' END));

    SELECT count(*) INTO v_over FROM gdd_l4_413_hubs WHERE active > capacity_slots;
    IF v_over > 0 THEN
        RAISE NOTICE 'gdd-l4-413: % relay hub(s) hold more active entries than slots: occupancy left unchanged', v_over;
    END IF;
END $$;
