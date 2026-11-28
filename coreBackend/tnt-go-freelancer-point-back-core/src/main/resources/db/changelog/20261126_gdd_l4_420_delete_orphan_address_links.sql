-- Go-Freelancer de-duplication, lot L4 correction (gdd-l4-420), maintainer decision D-134 (2026-11-26,
-- "Orphelins D-128 -> Supprimer"). DESTRUCTIVE CHANGE, EXPLICITLY DECIDED BY THE MAINTAINER (D-134).
--
-- After gdd-l4-417, an address link copied from person_addresses (gdd-l4-411, link id = person_addresses.id) whose
-- owner is not a known actor id cannot be read by anyone: the code reads the links by the actor of the token.
-- D-134: such links are deleted. A link is kept only when its owner has a safe correspondence to itself, i.e. it is
-- an actor id known through the same correspondences as gdd-l4-417 (lot L3 tables and core profiles). Deleted:
--   * links whose owner has no correspondence (legacy person / client id never linked to an actor);
--   * links whose owner is ambiguous (one legacy id, two actors);
--   * links left on a legacy id by gdd-l4-417 because the actor already held the same (address, label) link.
-- Only the rows of tnt_address_links that came from person_addresses are considered; the addresses themselves
-- (tnt_addresses) and the legacy tables are kept (H7). No table references tnt_address_links.
--
-- NOT deleted (D-134 is about addresses): the relay deposit entries of tnt_hub_package_entries (gdd-l4-412) whose
-- owner or picker is still a legacy id; they are only counted (RAISE NOTICE) for the administrator.
-- Re-runnable: a second run finds nothing to delete.
DO $$
DECLARE
    src              RECORD;
    n_links          BIGINT := 0;
    n_deleted        BIGINT := 0;
    n_del_none       BIGINT := 0;
    n_del_ambig      BIGINT := 0;
    n_del_dup        BIGINT := 0;
    n_entry_owner    BIGINT := 0;
    n_entry_picker   BIGINT := 0;
BEGIN
    CREATE TEMP TABLE _gdd4_orphan_pairs (legacy_id UUID, actor_id UUID) ON COMMIT DROP;

    IF to_regclass('public.gofp_users') IS NOT NULL THEN
        INSERT INTO _gdd4_orphan_pairs SELECT id, core_user_id FROM gofp_users;
        INSERT INTO _gdd4_orphan_pairs SELECT core_user_id, core_user_id FROM gofp_users;
    END IF;
    IF to_regclass('public.gofp_clients') IS NOT NULL THEN
        INSERT INTO _gdd4_orphan_pairs SELECT id, core_user_id FROM gofp_clients;
        INSERT INTO _gdd4_orphan_pairs SELECT core_client_id, core_user_id FROM gofp_clients;
    END IF;
    IF to_regclass('public.gofp_freelancers') IS NOT NULL THEN
        INSERT INTO _gdd4_orphan_pairs SELECT id, core_user_id FROM gofp_freelancers;
        INSERT INTO _gdd4_orphan_pairs SELECT core_freelancer_id, core_user_id FROM gofp_freelancers;
    END IF;
    FOR src IN
        SELECT t.tbl FROM (VALUES ('client_profiles'), ('freelancer_profiles'), ('relay_operator_profiles'),
                                  ('deliverer_profiles')) AS t(tbl)
          JOIN information_schema.columns c
            ON c.table_schema = 'tnt_actor' AND c.table_name = t.tbl AND c.column_name = 'actor_id'
    LOOP
        EXECUTE format('INSERT INTO _gdd4_orphan_pairs SELECT id, actor_id FROM tnt_actor.%I', src.tbl);
        EXECUTE format('INSERT INTO _gdd4_orphan_pairs SELECT actor_id, actor_id FROM tnt_actor.%I', src.tbl);
    END LOOP;
    IF to_regclass('public.gofp_users') IS NOT NULL AND EXISTS (
            SELECT 1 FROM information_schema.columns
             WHERE table_schema = 'public' AND table_name = 'persons' AND column_name = 'user_id') THEN
        INSERT INTO _gdd4_orphan_pairs
        SELECT p.id, u.core_user_id FROM persons p JOIN gofp_users u ON u.core_user_id = p.user_id;
    END IF;

    CREATE TEMP TABLE _gdd4_orphan_map ON COMMIT DROP AS
    SELECT legacy_id,
           CASE WHEN count(DISTINCT actor_id) = 1 THEN min(actor_id::text)::uuid END AS actor_id,
           count(DISTINCT actor_id) > 1 AS ambiguous
      FROM _gdd4_orphan_pairs
     WHERE legacy_id IS NOT NULL AND actor_id IS NOT NULL
     GROUP BY legacy_id;
    CREATE UNIQUE INDEX ON _gdd4_orphan_map (legacy_id);

    -- 1. Address links copied by gdd-l4-411 whose owner is not a known actor: deleted (D-134)
    IF to_regclass('public.person_addresses') IS NOT NULL
            AND to_regclass('tnt_geography.tnt_address_links') IS NOT NULL THEN
        CREATE TEMP TABLE _gdd4_orphan_links ON COMMIT DROP AS
        SELECT l.id,
               CASE WHEN m.legacy_id IS NULL THEN 'none'
                    WHEN m.ambiguous THEN 'ambiguous'
                    ELSE 'duplicate' END AS why
          FROM tnt_geography.tnt_address_links l
          JOIN person_addresses pa ON pa.id = l.id
          LEFT JOIN _gdd4_orphan_map m ON m.legacy_id = l.owner_id
         WHERE m.actor_id IS DISTINCT FROM l.owner_id;

        SELECT count(*) INTO n_links
          FROM tnt_geography.tnt_address_links l JOIN person_addresses pa ON pa.id = l.id;
        SELECT count(*) FILTER (WHERE why = 'none'), count(*) FILTER (WHERE why = 'ambiguous'),
               count(*) FILTER (WHERE why = 'duplicate')
          INTO n_del_none, n_del_ambig, n_del_dup
          FROM _gdd4_orphan_links;

        DELETE FROM tnt_geography.tnt_address_links l USING _gdd4_orphan_links o WHERE l.id = o.id;
        GET DIAGNOSTICS n_deleted = ROW_COUNT;

        RAISE NOTICE 'gdd-l4-420 (D-134) address links from person_addresses: % before, % deleted (% without '
            'correspondence, % ambiguous, % duplicating a link of the actor), % kept',
            n_links, n_deleted, n_del_none, n_del_ambig, n_del_dup, n_links - n_deleted;
    END IF;

    -- 2. Relay deposit entries copied by gdd-l4-412 still on a legacy id: counted only, never deleted
    IF to_regclass('public.relay_deposits') IS NOT NULL AND to_regclass('public.tnt_hub_package_entries') IS NOT NULL THEN
        SELECT count(*) INTO n_entry_owner
          FROM tnt_hub_package_entries e
          LEFT JOIN _gdd4_orphan_map m ON m.legacy_id = e.owner_actor_id
         WHERE e.owner_actor_id IS NOT NULL AND m.actor_id IS DISTINCT FROM e.owner_actor_id
           AND EXISTS (SELECT 1 FROM relay_deposits rd
                        WHERE rd.client_id = e.owner_actor_id AND (rd.id = e.id OR rd.logistics_id = e.hub_id));
        SELECT count(*) INTO n_entry_picker
          FROM tnt_hub_package_entries e
          LEFT JOIN _gdd4_orphan_map m ON m.legacy_id = e.picked_up_by_actor_id
         WHERE e.picked_up_by_actor_id IS NOT NULL AND m.actor_id IS DISTINCT FROM e.picked_up_by_actor_id
           AND EXISTS (SELECT 1 FROM relay_deposits rd
                        WHERE rd.client_id = e.picked_up_by_actor_id
                          AND (rd.id = e.id OR rd.logistics_id = e.hub_id));

        RAISE NOTICE 'gdd-l4-420 (D-134) relay deposit entries from relay_deposits still on a legacy id (kept, to be '
            'attached by an administrator): % by owner, % by picker', n_entry_owner, n_entry_picker;
    END IF;
END $$;
