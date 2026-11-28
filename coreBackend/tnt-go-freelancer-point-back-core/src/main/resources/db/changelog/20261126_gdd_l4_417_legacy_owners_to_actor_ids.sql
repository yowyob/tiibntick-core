-- Go-Freelancer de-duplication, lot L4 correction (gdd-l4-417), maintainer decision D-128 (2026-11-26):
-- the owners copied by gdd-l4-411 (tnt_address_links.owner_id = person_addresses.person_id) and by gdd-l4-412
-- (tnt_hub_package_entries.owner_actor_id / picked_up_by_actor_id = relay_deposits.client_id) still hold the legacy
-- Go-Freelancer id, whereas the code reads them by the actor of the token (H1: core ids everywhere).
-- gdd-l4-411/412 are already applied: they are never edited, this changeset converts their output.
--
-- Correspondence legacy id -> actor id. Only the correspondences already established by lot L3 (gdd-l3-302/310/311/
-- 312) and by the core profiles are used; nothing is guessed (no match on e-mail, name or phone):
--   * gofp_users.id or gofp_users.core_user_id           -> core_user_id (the actor id, gdd-l3-310)
--   * gofp_clients.id or gofp_clients.core_client_id     -> core_user_id (gdd-l3-302/311)
--   * gofp_freelancers.id or core_freelancer_id          -> core_user_id (gdd-l3-302/312, H9)
--   * id or actor_id of a tnt_actor profile (client, freelancer, relay operator, deliverer) -> its actor_id
--   * persons.id whose persons.user_id is a gofp_users.core_user_id (declared FK persons.user_id -> users.id, and
--     gofp_users.core_user_id is documented as that users id)                                   -> that core_user_id
-- A legacy id leading to two different actors is ambiguous and left as is. An id without correspondence is left
-- as is (it may already be an actor id unknown to these tables). Both are counted by RAISE NOTICE: nothing invented.
--
-- Rows touched: only the rows produced by gdd-l4-411 (link id = person_addresses.id) and by gdd-l4-412 (entry id =
-- relay_deposits.id, or entry of the deposit's hub whose owner / picker is still the deposit's client id).
-- A converted link that would duplicate an existing (owner, address, label) link (unique index of gdd-l4-401) is
-- left as is and counted (no row is deleted: no destructive change).
-- Re-runnable: a converted value is an actor id, which maps to itself.
DO $$
DECLARE
    src            RECORD;
    n_links        BIGINT := 0;
    n_links_actor  BIGINT := 0;
    n_links_done   BIGINT := 0;
    n_links_none   BIGINT := 0;
    n_links_ambig  BIGINT := 0;
    n_links_dup    BIGINT := 0;
    n_owner_done   BIGINT := 0;
    n_owner_none   BIGINT := 0;
    n_owner_ambig  BIGINT := 0;
    n_picker_done  BIGINT := 0;
    n_picker_none  BIGINT := 0;
BEGIN
    CREATE TEMP TABLE _gdd4_owner_pairs (legacy_id UUID, actor_id UUID) ON COMMIT DROP;

    IF to_regclass('public.gofp_users') IS NOT NULL THEN
        INSERT INTO _gdd4_owner_pairs SELECT id, core_user_id FROM gofp_users;
        INSERT INTO _gdd4_owner_pairs SELECT core_user_id, core_user_id FROM gofp_users;
    END IF;
    IF to_regclass('public.gofp_clients') IS NOT NULL THEN
        INSERT INTO _gdd4_owner_pairs SELECT id, core_user_id FROM gofp_clients;
        INSERT INTO _gdd4_owner_pairs SELECT core_client_id, core_user_id FROM gofp_clients;
    END IF;
    IF to_regclass('public.gofp_freelancers') IS NOT NULL THEN
        INSERT INTO _gdd4_owner_pairs SELECT id, core_user_id FROM gofp_freelancers;
        INSERT INTO _gdd4_owner_pairs SELECT core_freelancer_id, core_user_id FROM gofp_freelancers;
    END IF;
    FOR src IN
        SELECT t.tbl FROM (VALUES ('client_profiles'), ('freelancer_profiles'), ('relay_operator_profiles'),
                                  ('deliverer_profiles')) AS t(tbl)
          JOIN information_schema.columns c
            ON c.table_schema = 'tnt_actor' AND c.table_name = t.tbl AND c.column_name = 'actor_id'
    LOOP
        EXECUTE format('INSERT INTO _gdd4_owner_pairs SELECT id, actor_id FROM tnt_actor.%I', src.tbl);
        EXECUTE format('INSERT INTO _gdd4_owner_pairs SELECT actor_id, actor_id FROM tnt_actor.%I', src.tbl);
    END LOOP;
    IF to_regclass('public.gofp_users') IS NOT NULL AND EXISTS (
            SELECT 1 FROM information_schema.columns
             WHERE table_schema = 'public' AND table_name = 'persons' AND column_name = 'user_id') THEN
        INSERT INTO _gdd4_owner_pairs
        SELECT p.id, u.core_user_id FROM persons p JOIN gofp_users u ON u.core_user_id = p.user_id;
    END IF;

    CREATE TEMP TABLE _gdd4_owner_map ON COMMIT DROP AS
    SELECT legacy_id,
           CASE WHEN count(DISTINCT actor_id) = 1 THEN min(actor_id::text)::uuid END AS actor_id,
           count(DISTINCT actor_id) > 1 AS ambiguous
      FROM _gdd4_owner_pairs
     WHERE legacy_id IS NOT NULL AND actor_id IS NOT NULL
     GROUP BY legacy_id;
    CREATE UNIQUE INDEX ON _gdd4_owner_map (legacy_id);

    -- 1. Address links copied by gdd-l4-411
    IF to_regclass('public.person_addresses') IS NOT NULL
            AND to_regclass('tnt_geography.tnt_address_links') IS NOT NULL THEN
        CREATE TEMP TABLE _gdd4_links ON COMMIT DROP AS
        SELECT l.id, l.owner_id, l.address_id, l.label, m.actor_id, COALESCE(m.ambiguous, FALSE) AS ambiguous
          FROM tnt_geography.tnt_address_links l
          JOIN person_addresses pa ON pa.id = l.id
          LEFT JOIN _gdd4_owner_map m ON m.legacy_id = l.owner_id;

        SELECT count(*) INTO n_links FROM _gdd4_links;
        SELECT count(*) INTO n_links_actor FROM _gdd4_links WHERE actor_id = owner_id;
        SELECT count(*) INTO n_links_none FROM _gdd4_links WHERE actor_id IS NULL AND NOT ambiguous;
        SELECT count(*) INTO n_links_ambig FROM _gdd4_links WHERE ambiguous;

        CREATE TEMP TABLE _gdd4_links_todo ON COMMIT DROP AS
        SELECT x.id, x.actor_id
          FROM (SELECT k.*, row_number() OVER (PARTITION BY k.actor_id, k.address_id, COALESCE(k.label, '')
                                               ORDER BY k.id) AS rn
                  FROM _gdd4_links k
                 WHERE k.actor_id IS NOT NULL AND k.actor_id <> k.owner_id) x
         WHERE x.rn = 1
           AND NOT EXISTS (SELECT 1 FROM tnt_geography.tnt_address_links o
                            WHERE o.owner_id = x.actor_id AND o.address_id = x.address_id
                              AND COALESCE(o.label, '') = COALESCE(x.label, ''));

        UPDATE tnt_geography.tnt_address_links l SET owner_id = t.actor_id
          FROM _gdd4_links_todo t WHERE l.id = t.id;
        GET DIAGNOSTICS n_links_done = ROW_COUNT;
        SELECT count(*) - n_links_done INTO n_links_dup
          FROM _gdd4_links WHERE actor_id IS NOT NULL AND actor_id <> owner_id;

        RAISE NOTICE 'gdd-l4-417 address links from person_addresses: % in total, % already on an actor id, % converted, '
            '% without correspondence (left), % ambiguous (left), % left because the actor already has that link',
            n_links, n_links_actor, n_links_done, n_links_none, n_links_ambig, n_links_dup;
    END IF;

    -- 2. Hub package entries copied or completed by gdd-l4-412
    IF to_regclass('public.relay_deposits') IS NOT NULL AND to_regclass('public.tnt_hub_package_entries') IS NOT NULL THEN
        UPDATE tnt_hub_package_entries e SET owner_actor_id = m.actor_id
          FROM _gdd4_owner_map m
         WHERE m.legacy_id = e.owner_actor_id AND m.actor_id IS NOT NULL AND m.actor_id <> e.owner_actor_id
           AND EXISTS (SELECT 1 FROM relay_deposits rd
                        WHERE rd.client_id = e.owner_actor_id AND (rd.id = e.id OR rd.logistics_id = e.hub_id));
        GET DIAGNOSTICS n_owner_done = ROW_COUNT;

        UPDATE tnt_hub_package_entries e SET picked_up_by_actor_id = m.actor_id
          FROM _gdd4_owner_map m
         WHERE m.legacy_id = e.picked_up_by_actor_id AND m.actor_id IS NOT NULL
           AND m.actor_id <> e.picked_up_by_actor_id
           AND EXISTS (SELECT 1 FROM relay_deposits rd
                        WHERE rd.client_id = e.picked_up_by_actor_id AND (rd.id = e.id OR rd.logistics_id = e.hub_id));
        GET DIAGNOSTICS n_picker_done = ROW_COUNT;

        SELECT count(*) INTO n_owner_none
          FROM tnt_hub_package_entries e
         WHERE e.owner_actor_id IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM _gdd4_owner_map m WHERE m.legacy_id = e.owner_actor_id)
           AND EXISTS (SELECT 1 FROM relay_deposits rd
                        WHERE rd.client_id = e.owner_actor_id AND (rd.id = e.id OR rd.logistics_id = e.hub_id));
        SELECT count(*) INTO n_owner_ambig
          FROM tnt_hub_package_entries e
          JOIN _gdd4_owner_map m ON m.legacy_id = e.owner_actor_id AND m.ambiguous
         WHERE EXISTS (SELECT 1 FROM relay_deposits rd
                        WHERE rd.client_id = e.owner_actor_id AND (rd.id = e.id OR rd.logistics_id = e.hub_id));
        SELECT count(*) INTO n_picker_none
          FROM tnt_hub_package_entries e
         WHERE e.picked_up_by_actor_id IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM _gdd4_owner_map m WHERE m.legacy_id = e.picked_up_by_actor_id)
           AND EXISTS (SELECT 1 FROM relay_deposits rd
                        WHERE rd.client_id = e.picked_up_by_actor_id
                          AND (rd.id = e.id OR rd.logistics_id = e.hub_id));

        RAISE NOTICE 'gdd-l4-417 hub package entries from relay_deposits: owner converted on % rows, % without correspondence '
            '(left), % ambiguous (left); picked_up_by converted on % rows, % without correspondence (left)',
            n_owner_done, n_owner_none, n_owner_ambig, n_picker_done, n_picker_none;
    END IF;
END $$;
