-- Go-Freelancer de-duplication, lot L4 (gdd-l4-415): every *_address_id column of the module now holds an id of the
-- core address book (tnt_geography.tnt_addresses: the legacy ids were copied by gdd-l4-410, new addresses are only
-- written there), so every foreign key to the legacy public.addresses table is dropped, whatever its table or
-- generated name (delivery_needs, announcements, gofp_users/clients/freelancers/relay_points, person_addresses, ...).
-- The columns keep their names and types; the legacy table and its rows are kept (H7). Consequence: deleting a
-- legacy address no longer restricts, nulls or cascades anything. Re-runnable: once dropped the loop finds nothing.
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT c.conname, n.nspname AS schema_name, t.relname AS table_name
          FROM pg_constraint c
          JOIN pg_class t      ON t.oid = c.conrelid
          JOIN pg_namespace n  ON n.oid = t.relnamespace
          JOIN pg_class rt     ON rt.oid = c.confrelid
          JOIN pg_namespace rn ON rn.oid = rt.relnamespace
         WHERE c.contype = 'f'
           AND rt.relname = 'addresses'
           AND rn.nspname = 'public'
    LOOP
        EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT IF EXISTS %I', fk.schema_name, fk.table_name, fk.conname);
    END LOOP;
END $$;
