-- Go-Freelancer de-duplication, lot L4 (gdd-l4-411): person/address links move to the core address book.
-- owner_id keeps the legacy person_id as is: its conversion to the actor id belongs to the actor lot (L3).
-- The link label is the type of the linked address (addresses.type: NOT NULL, default PRIMARY; blank -> no label).
-- Re-runnable.
DO $$
DECLARE
    label_expr text := CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns
                                          WHERE table_schema = 'public' AND table_name = 'addresses'
                                            AND column_name = 'type')
                            THEN 'NULLIF(btrim(a.type::text), '''')' ELSE 'NULL::text' END;
BEGIN
    EXECUTE 'INSERT INTO tnt_geography.tnt_address_links (id, owner_id, address_id, label, created_at) '
         || 'SELECT pa.id, pa.person_id, pa.address_id, ' || label_expr || ', now() '
         || 'FROM person_addresses pa JOIN addresses a ON a.id = pa.address_id '
         || 'WHERE EXISTS (SELECT 1 FROM tnt_geography.tnt_addresses t WHERE t.id = pa.address_id) '
         || 'ON CONFLICT DO NOTHING';
END $$;
