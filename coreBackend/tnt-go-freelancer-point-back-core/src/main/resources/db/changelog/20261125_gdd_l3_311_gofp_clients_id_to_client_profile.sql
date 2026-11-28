-- gdd-l3-311 : gofp_clients.id becomes the id of the actor-core client profile (3.10 e).
--
-- The client profile is the one whose id is core_client_id; otherwise the only client profile of the
-- actor core_user_id (several profiles for the actor: ambiguous, the row is left and counted).
-- Diagnostic (RAISE NOTICE): rows without client profile, rows realigned, rows left.
-- Same foreign-key pattern as gdd-l3-310 (pg_constraint, on gofp_clients(id) only).
-- Idempotent: an aligned row maps to itself and is skipped.
DO $$
DECLARE
    fk           RECORD;
    n_total      BIGINT;
    n_no_profile BIGINT;
    n_mapped     BIGINT;
BEGIN
    CREATE TEMP TABLE _gdd3_client_target ON COMMIT DROP AS
    SELECT c.id AS old_id,
           COALESCE(
               (SELECT cp.id FROM tnt_actor.client_profiles cp WHERE cp.id = c.core_client_id),
               (SELECT min(cp.id::text)::uuid FROM tnt_actor.client_profiles cp
                 WHERE cp.actor_id = c.core_user_id
                HAVING count(*) = 1)) AS new_id
      FROM gofp_clients c;

    SELECT count(*) INTO n_total FROM _gdd3_client_target;
    SELECT count(*) INTO n_no_profile FROM _gdd3_client_target WHERE new_id IS NULL;
    RAISE NOTICE 'gdd-l3-311 diagnostic: % gofp_clients rows, % without a single matching client profile',
        n_total, n_no_profile;

    CREATE TEMP TABLE _gdd3_client_ids ON COMMIT DROP AS
    SELECT t.old_id, t.new_id
      FROM _gdd3_client_target t
     WHERE t.new_id IS NOT NULL
       AND t.new_id <> t.old_id
       AND NOT EXISTS (SELECT 1 FROM gofp_clients o WHERE o.id = t.new_id)
       AND (SELECT count(*) FROM _gdd3_client_target d WHERE d.new_id = t.new_id) = 1;
    SELECT count(*) INTO n_mapped FROM _gdd3_client_ids;
    RAISE NOTICE 'gdd-l3-311: % rows realigned on their client profile id', n_mapped;
    IF n_mapped = 0 THEN
        RETURN;
    END IF;

    CREATE TEMP TABLE _gdd3_client_fks ON COMMIT DROP AS
    SELECT c.conrelid::regclass::text  AS child,
           c.conname                   AS name,
           a.attname                   AS col,
           pg_get_constraintdef(c.oid) AS def
      FROM pg_constraint c
      JOIN pg_attribute a  ON a.attrelid = c.conrelid  AND a.attnum = c.conkey[1]
      JOIN pg_attribute ra ON ra.attrelid = c.confrelid AND ra.attnum = c.confkey[1]
     WHERE c.contype = 'f'
       AND c.confrelid = 'gofp_clients'::regclass
       AND ra.attname = 'id'
       AND array_length(c.conkey, 1) = 1;

    FOR fk IN SELECT * FROM _gdd3_client_fks LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.child, fk.name);
    END LOOP;

    UPDATE gofp_clients c SET id = m.new_id FROM _gdd3_client_ids m WHERE c.id = m.old_id;
    FOR fk IN SELECT * FROM _gdd3_client_fks LOOP
        EXECUTE format('UPDATE %s t SET %I = m.new_id FROM _gdd3_client_ids m WHERE t.%I = m.old_id',
                       fk.child, fk.col, fk.col);
    END LOOP;

    FOR fk IN SELECT * FROM _gdd3_client_fks LOOP
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', fk.child, fk.name, fk.def);
    END LOOP;
END $$;
