-- gdd-final-901 (D-154): census of the legacy Go-Freelancer rows that have no counterpart in the core.
--
-- Every drop of the closure changesets is preceded by a precondition reading this census
-- (onFail = HALT): a legacy row without a core counterpart stops the migration before anything is
-- dropped, so that no data is lost silently. Diagnostic in production:
--   SELECT * FROM public.gdd_final_uncovered() WHERE uncovered > 0;
--
-- Not counted (no counterpart by decision): the local password hashes and reset tokens (authentication
-- belongs to the Kernel, D-054), the local notification feed (no reader, decision of lot L4-2).
-- Known fixtures are not counted either: the demo rows of changeset 040 (fixed ids 1111…, 2222…, 3333…,
-- 4444…, 5555…, 6666…), the guest client (changeset 033) and the local administrator that the removed
-- AdminBootstrapConfig wrote at each startup (fixed id …0002).
--
-- The functions are dropped by the last closure changeset (gdd-final-909).

CREATE OR REPLACE FUNCTION public.gdd_final_fixture(v UUID) RETURNS BOOLEAN
LANGUAGE sql IMMUTABLE AS $fn$
    SELECT v IS NOT NULL AND (
           v::text IN ('00000000-0000-0000-0000-000000000000',
                       '00000000-0000-0000-0000-000000000001',
                       '00000000-0000-0000-0000-000000000002')
        OR v::text ~ '^(11111111-1111-1111-1111-1111111111|22222222-2222-2222-2222-2222222222|33333333-3333-3333-3333-3333333333|44444444-4444-4444-4444-4444444444|55555555-5555-5555-5555-5555555555|66666666-6666-6666-6666-6666666666)[0-9]{2}$')
$fn$;

-- An id the core or the gofp projection knows as an actor or a profile.
CREATE OR REPLACE FUNCTION public.gdd_final_known_actor(v UUID) RETURNS BOOLEAN
LANGUAGE plpgsql STABLE AS $fn$
BEGIN
    IF v IS NULL THEN
        RETURN FALSE;
    END IF;
    RETURN EXISTS (SELECT 1 FROM gofp_users u WHERE v IN (u.id, u.core_user_id))
        OR EXISTS (SELECT 1 FROM gofp_clients c WHERE v IN (c.id, c.core_client_id, c.core_user_id))
        OR EXISTS (SELECT 1 FROM gofp_freelancers f WHERE v IN (f.id, f.core_freelancer_id, f.core_user_id))
        OR EXISTS (SELECT 1 FROM tnt_actor.client_profiles p WHERE v IN (p.id, p.actor_id))
        OR EXISTS (SELECT 1 FROM tnt_actor.freelancer_profiles p WHERE v IN (p.id, p.actor_id))
        OR EXISTS (SELECT 1 FROM tnt_actor.deliverer_profiles p WHERE v IN (p.id, p.actor_id))
        OR EXISTS (SELECT 1 FROM tnt_actor.relay_operator_profiles p WHERE v IN (p.id, p.actor_id));
END
$fn$;

-- A legacy person that is a fixture or an actor of the new model.
CREATE OR REPLACE FUNCTION public.gdd_final_person_covered(v UUID) RETURNS BOOLEAN
LANGUAGE plpgsql STABLE AS $fn$
BEGIN
    IF v IS NULL THEN
        RETURN FALSE;
    END IF;
    IF public.gdd_final_fixture(v) OR public.gdd_final_known_actor(v) THEN
        RETURN TRUE;
    END IF;
    IF to_regclass('public.persons') IS NULL THEN
        RETURN FALSE;
    END IF;
    RETURN EXISTS (SELECT 1 FROM persons p
                    WHERE p.id = v
                      AND (lower(p.email) = 'guest@tiibntick.com'
                           OR (p.user_id IS NOT NULL AND public.gdd_final_known_actor(p.user_id))));
END
$fn$;

-- A legacy logistics row (vehicle or relay shop) the new model knows.
CREATE OR REPLACE FUNCTION public.gdd_final_logistics_covered(v UUID) RETURNS BOOLEAN
LANGUAGE plpgsql STABLE AS $fn$
BEGIN
    IF v IS NULL THEN
        RETURN FALSE;
    END IF;
    RETURN public.gdd_final_fixture(v)
        OR EXISTS (SELECT 1 FROM gofp_relay_points r WHERE v IN (r.id, r.core_relay_point_id))
        OR EXISTS (SELECT 1 FROM gofreelancer_vehicles g WHERE v IN (g.id, g.core_vehicle_id))
        OR EXISTS (SELECT 1 FROM tnt_freelancer_vehicles t WHERE t.id = v)
        OR (to_regclass('public.logistics') IS NOT NULL AND EXISTS (
                SELECT 1 FROM logistics l
                 WHERE l.id = v AND NULLIF(trim(l.plate_number), '') IS NOT NULL
                   AND (EXISTS (SELECT 1 FROM tnt_freelancer_vehicles t
                                 WHERE upper(trim(t.plate_number)) = upper(trim(l.plate_number)))
                        OR EXISTS (SELECT 1 FROM gofreelancer_vehicles g
                                    WHERE upper(trim(g.plate_number)) = upper(trim(l.plate_number))))));
END
$fn$;

-- One row per closure item: the number of legacy rows that would be lost by the drop.
CREATE OR REPLACE FUNCTION public.gdd_final_uncovered() RETURNS TABLE (item TEXT, uncovered BIGINT)
LANGUAGE plpgsql STABLE AS $fn$
DECLARE
    n BIGINT;
BEGIN
    -- data copied to the core by lots L1 to L4 (same ids)
    n := 0;
    IF to_regclass('public.announcements') IS NOT NULL THEN
        SELECT count(*) INTO n FROM announcements l
         WHERE NOT EXISTS (SELECT 1 FROM tnt_delivery_announcements a WHERE a.id = l.id);
    END IF;
    item := 'announcements'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.responses') IS NOT NULL THEN
        SELECT count(*) INTO n FROM responses r
         WHERE NOT EXISTS (SELECT 1 FROM tnt_announcement_responses a WHERE a.id = r.id);
    END IF;
    item := 'responses'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.packets') IS NOT NULL THEN
        SELECT count(*) INTO n FROM packets p WHERE NOT EXISTS (SELECT 1 FROM tnt_parcels t WHERE t.id = p.id);
    END IF;
    item := 'packets'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.evaluations') IS NOT NULL THEN
        SELECT count(*) INTO n FROM evaluations e WHERE NOT EXISTS (SELECT 1 FROM tnt_tp_ratings t WHERE t.id = e.id);
    END IF;
    item := 'evaluations'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.payments') IS NOT NULL THEN
        SELECT count(*) INTO n FROM payments p
         WHERE upper(p.status) = 'PAID'
           AND NOT EXISTS (SELECT 1 FROM billing.wallet_settlements s WHERE s.id = p.id);
    END IF;
    item := 'payments'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.relay_deposits') IS NOT NULL THEN
        SELECT count(*) INTO n FROM relay_deposits rd
         WHERE NOT EXISTS (SELECT 1 FROM tnt_hub_package_entries e
                            WHERE e.id = rd.id
                               OR e.package_id = rd.packet_id
                               OR e.tracking_code = upper(rd.packet_id::text)
                               OR e.package_id IN (SELECT dn.id FROM delivery_needs dn
                                                    WHERE dn.parcel_id = rd.packet_id));
    END IF;
    item := 'relay_deposits'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.addresses') IS NOT NULL THEN
        SELECT count(*) INTO n FROM addresses a
         WHERE NOT EXISTS (SELECT 1 FROM tnt_geography.tnt_addresses t WHERE t.id = a.id);
    END IF;
    item := 'addresses'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.person_addresses') IS NOT NULL THEN
        SELECT count(*) INTO n FROM person_addresses pa
         WHERE NOT EXISTS (SELECT 1 FROM tnt_geography.tnt_address_links l WHERE l.id = pa.id)
           AND NOT public.gdd_final_fixture(pa.person_id);
    END IF;
    item := 'person_addresses'; uncovered := n; RETURN NEXT;

    -- legacy price grids: each owner must have its FREELANCER-GRID billing policy (lot L4 backfill)
    n := 0;
    IF to_regclass('public.delivery_person_pricing') IS NOT NULL THEN
        SELECT count(*) INTO n FROM delivery_person_pricing g
         WHERE NOT EXISTS (SELECT 1 FROM pricing.billing_policy b
                            WHERE b.owner_actor_id = g.delivery_person_id::text
                              AND b.template_code = 'FREELANCER-GRID');
    END IF;
    item := 'delivery_person_pricing'; uncovered := n; RETURN NEXT;

    -- deliveries: agreed terms and lifecycle are read from the core delivery of the same id (H7, gdd-l1-107)
    n := 0;
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'deliveries' AND column_name = 'tarif') THEN
        SELECT count(*) INTO n FROM deliveries g
         WHERE (g.tarif IS NOT NULL OR g.status IS NOT NULL OR g.freelancer_id IS NOT NULL
                OR g.pickup_min_time IS NOT NULL OR g.pickup_max_time IS NOT NULL
                OR g.delivery_min_time IS NOT NULL OR g.delivery_max_time IS NOT NULL
                OR g.actual_pickup_time IS NOT NULL OR g.actual_delivery_time IS NOT NULL)
           AND NOT EXISTS (SELECT 1 FROM tnt_deliveries d WHERE d.id = g.id);
    END IF;
    item := 'deliveries.legacy_columns'; uncovered := n; RETURN NEXT;

    -- delivery needs: packet copied to parcel_id (gdd-l2-210); an assignment stored before lot L2 must have
    -- its delivery in the core (the read fallback of lot L2 is removed with the columns)
    n := 0;
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'delivery_needs' AND column_name = 'packet_id') THEN
        SELECT count(*) INTO n FROM delivery_needs dn WHERE dn.packet_id IS NOT NULL AND dn.parcel_id IS NULL;
    END IF;
    item := 'delivery_needs.packet_id'; uncovered := n; RETURN NEXT;

    n := 0;
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'delivery_needs' AND column_name = 'status') THEN
        SELECT count(*) INTO n FROM delivery_needs dn
         WHERE upper(trim(dn.status)) IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED')
           AND NOT EXISTS (SELECT 1 FROM tnt_delivery_announcements a
                            WHERE (a.id = dn.announcement_id OR a.origin_ref_id = dn.id)
                              AND a.created_delivery_id IS NOT NULL);
    END IF;
    item := 'delivery_needs.legacy_state'; uncovered := n; RETURN NEXT;

    -- profile aggregates read from the core: loyalty (gdd-l3-303) and rating averages (gdd-l3-304) migrated
    n := 0;
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'gofp_clients' AND column_name = 'loyalty_status') THEN
        SELECT 2 - count(DISTINCT id) INTO n FROM databasechangelog
         WHERE id IN ('gdd-l3-303-loyalty-accounts-from-orders', 'gdd-l3-304-rating-averages-from-tp-ratings');
    END IF;
    item := 'gofp_profiles.aggregates'; uncovered := n; RETURN NEXT;

    -- legacy identity tables (before the gofp_* projection): only fixtures or known actors may remain
    n := 0;
    IF to_regclass('public.users') IS NOT NULL THEN
        SELECT count(*) INTO n FROM users u
         WHERE NOT public.gdd_final_fixture(u.id) AND NOT public.gdd_final_known_actor(u.id);
    END IF;
    item := 'users'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.persons') IS NOT NULL THEN
        SELECT count(*) INTO n FROM persons p WHERE NOT public.gdd_final_person_covered(p.id);
    END IF;
    item := 'persons'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.clients') IS NOT NULL THEN
        SELECT count(*) INTO n FROM clients c
         WHERE NOT public.gdd_final_fixture(c.id) AND NOT public.gdd_final_known_actor(c.id)
           AND NOT public.gdd_final_person_covered(c.person_id);
    END IF;
    item := 'clients'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.delivery_persons') IS NOT NULL THEN
        SELECT count(*) INTO n FROM delivery_persons d
         WHERE NOT public.gdd_final_fixture(d.id) AND NOT public.gdd_final_known_actor(d.id)
           AND NOT public.gdd_final_person_covered(d.person_id);
    END IF;
    item := 'delivery_persons'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.logistics') IS NOT NULL THEN
        SELECT count(*) INTO n FROM logistics l WHERE NOT public.gdd_final_logistics_covered(l.id);
    END IF;
    item := 'logistics'; uncovered := n; RETURN NEXT;

    -- live tables whose foreign key to a legacy table is dropped: no row may still point at a legacy-only id
    n := 0;
    IF to_regclass('public.delivery_persons') IS NOT NULL THEN
        SELECT count(*) INTO n FROM (
            SELECT delivery_person_id AS v FROM announcement_subscriptions
            UNION ALL SELECT delivery_person_id FROM delivery_person_availability
            UNION ALL SELECT delivery_person_id FROM freelancer_eligibility
            UNION ALL SELECT delivery_person_id FROM subscriptions) r
         WHERE r.v IN (SELECT id FROM delivery_persons)
           AND NOT public.gdd_final_fixture(r.v) AND NOT public.gdd_final_known_actor(r.v);
    END IF;
    item := 'live_refs.delivery_persons'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.logistics') IS NOT NULL THEN
        SELECT count(*) INTO n FROM (
            SELECT logistics_id AS v FROM logistics_pricing
            UNION ALL SELECT relay_point_id FROM relay_point_subscriptions) r
         WHERE r.v IN (SELECT id FROM logistics)
           AND NOT public.gdd_final_logistics_covered(r.v);
    END IF;
    item := 'live_refs.logistics'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.persons') IS NOT NULL THEN
        SELECT count(*) INTO n FROM (
            SELECT person_id AS v FROM comments
            UNION ALL SELECT person_receiver_id FROM comments) r
         WHERE r.v IN (SELECT id FROM persons) AND NOT public.gdd_final_person_covered(r.v);
    END IF;
    item := 'live_refs.persons'; uncovered := n; RETURN NEXT;

    n := 0;
    IF to_regclass('public.users') IS NOT NULL THEN
        SELECT count(*) INTO n FROM delivery_needs dn
         WHERE dn.user_id IN (SELECT id FROM users)
           AND NOT public.gdd_final_fixture(dn.user_id) AND NOT public.gdd_final_known_actor(dn.user_id);
    END IF;
    item := 'live_refs.users'; uncovered := n; RETURN NEXT;
END
$fn$;
