-- Go-Freelancer de-duplication, lot L4 (gdd-l4-412): relay deposits become inventory hub package entries.
-- * A deposit already mirrored in the inventory (same hub, and the entry's package is the deposit's packet, or its
--   tracking code is that packet id, or its package is a delivery need whose packet is the deposit's packet) only
--   completes that entry: owner, fees, currency, package id = the deposit's packet id, pick-up when RETRIEVED.
-- * Any other deposit is inserted with the SAME id, the tenant of its geo relay hub, tracking code = the packet id
--   (the deposit id when that code is already taken). A deposit whose hub is unknown to geo is NOT copied, nor one
--   whose insert still conflicts (tracking code taken meanwhile); both are counted in a NOTICE.
-- * Finally every inventory entry still keyed on a delivery need id (package_id = delivery_needs.id whose packet_id,
--   since gdd-l1-104/106 the core parcel id, is set), or whose tracking code is that need id, is re-keyed on the parcel
--   id; its tracking code too when it was the need id and the parcel code is free (one entry per parcel takes it).
-- Re-runnable: nothing already present is overwritten.
DO $$
DECLARE
    v_without_hub BIGINT;
    v_unmatched   BIGINT;
BEGIN
    IF to_regclass('public.relay_deposits') IS NULL OR to_regclass('public.tnt_hub_package_entries') IS NULL THEN
        RETURN;
    END IF;

    DROP TABLE IF EXISTS gdd_l4_412_match;
    CREATE TEMP TABLE gdd_l4_412_match ON COMMIT DROP AS
    SELECT DISTINCT ON (e.id) e.id AS entry_id, rd.id AS deposit_id
      FROM relay_deposits rd
      JOIN tnt_hub_package_entries e ON e.hub_id = rd.logistics_id
     WHERE e.package_id = rd.packet_id
        OR e.tracking_code = upper(rd.packet_id::text)
        OR e.package_id IN (SELECT dn.id FROM delivery_needs dn WHERE dn.packet_id = rd.packet_id)
     ORDER BY e.id, rd.created_at DESC NULLS LAST;

    UPDATE tnt_hub_package_entries e
       SET owner_actor_id        = COALESCE(e.owner_actor_id, rd.client_id),
           storage_fee           = COALESCE(e.storage_fee, round(rd.storage_fee::numeric, 2)),
           penalty_fee           = COALESCE(e.penalty_fee, round(rd.penalty_fee::numeric, 2)),
           fee_currency          = COALESCE(e.fee_currency,
                                            CASE WHEN length(btrim(rd.currency)) BETWEEN 1 AND 3
                                                 THEN upper(btrim(rd.currency)) END),
           package_id            = rd.packet_id,
           picked_up_at          = COALESCE(e.picked_up_at,
                                            CASE WHEN rd.status = 'RETRIEVED'
                                                 THEN COALESCE(rd.retrieved_at, rd.created_at, now()) END),
           picked_up_by_actor_id = CASE WHEN e.picked_up_at IS NULL AND rd.status = 'RETRIEVED'
                                        THEN rd.client_id ELSE e.picked_up_by_actor_id END
      FROM gdd_l4_412_match m
      JOIN relay_deposits rd ON rd.id = m.deposit_id
     WHERE e.id = m.entry_id;

    INSERT INTO tnt_hub_package_entries (id, tenant_id, hub_id, package_id, tracking_code, deposited_at,
                                         picked_up_at, picked_up_by_actor_id, notified,
                                         owner_actor_id, storage_fee, penalty_fee, fee_currency)
    SELECT rd.id, h.tenant_id, rd.logistics_id, rd.packet_id,
           CASE WHEN EXISTS (SELECT 1 FROM tnt_hub_package_entries x WHERE x.tracking_code = upper(rd.packet_id::text))
                  OR EXISTS (SELECT 1 FROM relay_deposits o
                              WHERE o.packet_id = rd.packet_id AND o.id <> rd.id
                                AND (o.created_at, o.id) > (rd.created_at, rd.id))
                THEN upper(rd.id::text) ELSE upper(rd.packet_id::text) END,
           COALESCE(rd.created_at, now()),
           CASE WHEN rd.status = 'RETRIEVED' THEN COALESCE(rd.retrieved_at, rd.created_at, now()) END,
           CASE WHEN rd.status = 'RETRIEVED' THEN rd.client_id END,
           FALSE,
           rd.client_id, round(rd.storage_fee::numeric, 2), round(rd.penalty_fee::numeric, 2),
           CASE WHEN length(btrim(rd.currency)) BETWEEN 1 AND 3 THEN upper(btrim(rd.currency)) END
      FROM relay_deposits rd
      JOIN tnt_geography.relay_hubs h ON h.id = rd.logistics_id
     WHERE NOT EXISTS (SELECT 1 FROM gdd_l4_412_match m WHERE m.deposit_id = rd.id)
    ON CONFLICT DO NOTHING;

    DROP TABLE IF EXISTS gdd_l4_412_rekey;
    CREATE TEMP TABLE gdd_l4_412_rekey ON COMMIT DROP AS
    SELECT e.id AS entry_id, dn.packet_id AS parcel_id,
           (e.tracking_code = upper(dn.id::text)
             AND NOT EXISTS (SELECT 1 FROM tnt_hub_package_entries x WHERE x.tracking_code = upper(dn.packet_id::text))
             AND row_number() OVER (PARTITION BY dn.packet_id
                                    ORDER BY (e.picked_up_at IS NULL) DESC, e.deposited_at DESC, e.id) = 1) AS take_code
      FROM tnt_hub_package_entries e
      JOIN delivery_needs dn ON dn.id = e.package_id OR upper(dn.id::text) = e.tracking_code
     WHERE dn.packet_id IS NOT NULL
       AND (e.package_id = dn.id OR e.tracking_code = upper(dn.id::text));

    UPDATE tnt_hub_package_entries e
       SET package_id    = r.parcel_id,
           tracking_code = CASE WHEN r.take_code THEN upper(r.parcel_id::text) ELSE e.tracking_code END
      FROM gdd_l4_412_rekey r
     WHERE e.id = r.entry_id;

    SELECT count(*) INTO v_without_hub
      FROM relay_deposits rd
     WHERE NOT EXISTS (SELECT 1 FROM tnt_geography.relay_hubs h WHERE h.id = rd.logistics_id)
       AND NOT EXISTS (SELECT 1 FROM tnt_hub_package_entries e WHERE e.id = rd.id);
    IF v_without_hub > 0 THEN
        RAISE NOTICE 'gdd-l4-412: % relay deposit(s) not copied: their hub is unknown to tnt_geography.relay_hubs',
            v_without_hub;
    END IF;

    SELECT count(*) INTO v_unmatched
      FROM relay_deposits rd
     WHERE EXISTS (SELECT 1 FROM tnt_geography.relay_hubs h WHERE h.id = rd.logistics_id)
       AND NOT EXISTS (SELECT 1 FROM tnt_hub_package_entries e WHERE e.id = rd.id)
       AND NOT EXISTS (SELECT 1 FROM gdd_l4_412_match m WHERE m.deposit_id = rd.id);
    IF v_unmatched > 0 THEN
        RAISE NOTICE 'gdd-l4-412: % relay deposit(s) without an inventory entry and not matched to one (insert conflict, '
            'e.g. tracking code already taken)', v_unmatched;
    END IF;
END $$;
