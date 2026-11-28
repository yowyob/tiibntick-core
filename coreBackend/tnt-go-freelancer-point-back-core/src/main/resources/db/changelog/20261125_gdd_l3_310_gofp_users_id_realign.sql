-- gdd-l3-310 : gofp_users.id realigned on core_user_id (the actor id, H1 / 3.10 e).
--
-- Diagnostic (RAISE NOTICE, see the Liquibase log): rows with id <> core_user_id, rows realigned, rows
-- left because the target id is already the id of another row.
-- Pattern of 20261114_relocate_guest_person.sql: the foreign keys pointing at gofp_users(id) are read
-- from pg_constraint, dropped, their columns rewritten, then re-created with the same definition.
-- Foreign keys on gofp_users(core_user_id) are untouched (core_user_id does not change).
-- Idempotent: once realigned no row has id <> core_user_id and the block does nothing.
DO $$
DECLARE
    fk          RECORD;
    n_diverging BIGINT;
    n_mapped    BIGINT;
BEGIN
    SELECT count(*) INTO n_diverging FROM gofp_users WHERE id <> core_user_id;
    RAISE NOTICE 'gdd-l3-310 diagnostic: % gofp_users rows with id <> core_user_id', n_diverging;
    IF n_diverging = 0 THEN
        RETURN;
    END IF;

    CREATE TEMP TABLE _gdd3_user_ids ON COMMIT DROP AS
    SELECT u.id AS old_id, u.core_user_id AS new_id
      FROM gofp_users u
     WHERE u.id <> u.core_user_id
       AND NOT EXISTS (SELECT 1 FROM gofp_users o WHERE o.id = u.core_user_id);
    SELECT count(*) INTO n_mapped FROM _gdd3_user_ids;
    RAISE NOTICE 'gdd-l3-310: % rows realigned, % rows left (target id already used)',
        n_mapped, n_diverging - n_mapped;

    CREATE TEMP TABLE _gdd3_user_fks ON COMMIT DROP AS
    SELECT c.conrelid::regclass::text  AS child,
           c.conname                   AS name,
           a.attname                   AS col,
           pg_get_constraintdef(c.oid) AS def
      FROM pg_constraint c
      JOIN pg_attribute a  ON a.attrelid = c.conrelid  AND a.attnum = c.conkey[1]
      JOIN pg_attribute ra ON ra.attrelid = c.confrelid AND ra.attnum = c.confkey[1]
     WHERE c.contype = 'f'
       AND c.confrelid = 'gofp_users'::regclass
       AND ra.attname = 'id'
       AND array_length(c.conkey, 1) = 1;

    FOR fk IN SELECT * FROM _gdd3_user_fks LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.child, fk.name);
    END LOOP;

    UPDATE gofp_users u SET id = m.new_id FROM _gdd3_user_ids m WHERE u.id = m.old_id;
    FOR fk IN SELECT * FROM _gdd3_user_fks LOOP
        EXECUTE format('UPDATE %s t SET %I = m.new_id FROM _gdd3_user_ids m WHERE t.%I = m.old_id',
                       fk.child, fk.col, fk.col);
    END LOOP;

    FOR fk IN SELECT * FROM _gdd3_user_fks LOOP
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', fk.child, fk.name, fk.def);
    END LOOP;
END $$;
