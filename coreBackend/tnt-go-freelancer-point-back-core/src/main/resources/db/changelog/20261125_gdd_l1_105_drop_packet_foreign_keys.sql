-- gdd-l1-105: delivery_needs.packet_id and relay_deposits.packet_id now hold tnt-delivery-core parcel ids,
-- which are not rows of `packets`: every foreign key from these columns to `packets` is dropped, whatever
-- its generated name. The columns keep their names and types (the API contract is unchanged).
-- Consequence: deleting a parcel no longer cascades onto relay_deposits (see the lot L1 report).
-- Re-runnable: once the constraints are gone the loop finds nothing.
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT c.conname, t.relname AS table_name
          FROM pg_constraint c
          JOIN pg_class t  ON t.oid = c.conrelid
          JOIN pg_class rt ON rt.oid = c.confrelid
          JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
         WHERE c.contype = 'f'
           AND rt.relname = 'packets'
           AND t.relname IN ('delivery_needs', 'relay_deposits')
           AND a.attname = 'packet_id'
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT IF EXISTS %I', fk.table_name, fk.conname);
    END LOOP;
END $$;
