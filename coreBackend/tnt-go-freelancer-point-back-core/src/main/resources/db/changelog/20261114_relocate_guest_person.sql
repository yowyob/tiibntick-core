-- Corrective migration for changeset 033-insert-guest-client (already applied in production, never edited).
--
-- 033 inserted the Guest person under a fixed, all-zero-prefixed primary key. This gives the Guest
-- person a regular random id instead and repoints every reference to it (clients.person_id and every
-- foreign key declared on persons(id)). The row is located by its natural attributes, and the legacy
-- id is recognised by pattern, so this file does not carry the legacy literal itself. Idempotent:
-- once relocated the id no longer matches the pattern and the block does nothing.
-- The Guest *client* row is left untouched (its id is referenced by frontends).
DO $$
DECLARE
    old_id UUID;
    new_id UUID := gen_random_uuid();
    fk     RECORD;
BEGIN
    SELECT id INTO old_id
      FROM persons
     WHERE email = 'guest@tiibntick.com'
       AND national_id = 'GUEST_NATIONAL_ID'
       AND id::text ~ '^0{8}-0{4}-0{4}-0{4}-0{11}1$'
     LIMIT 1;

    IF old_id IS NULL THEN
        RETURN;
    END IF;

    -- Foreign keys pointing at persons(id): drop, remember the definition, re-create afterwards.
    CREATE TEMP TABLE _guest_fks ON COMMIT DROP AS
    SELECT c.conrelid::regclass::text AS child,
           c.conname                  AS name,
           a.attname                  AS col,
           pg_get_constraintdef(c.oid) AS def
      FROM pg_constraint c
      JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
     WHERE c.contype = 'f'
       AND c.confrelid = 'persons'::regclass
       AND array_length(c.conkey, 1) = 1;

    FOR fk IN SELECT * FROM _guest_fks LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.child, fk.name);
    END LOOP;

    UPDATE persons SET id = new_id WHERE id = old_id;
    FOR fk IN SELECT * FROM _guest_fks LOOP
        EXECUTE format('UPDATE %s SET %I = $1 WHERE %I = $2', fk.child, fk.col, fk.col)
          USING new_id, old_id;
    END LOOP;
    -- clients.person_id may be a plain column without a declared foreign key
    UPDATE clients SET person_id = new_id WHERE person_id = old_id;

    FOR fk IN SELECT * FROM _guest_fks LOOP
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', fk.child, fk.name, fk.def);
    END LOOP;
END $$;
