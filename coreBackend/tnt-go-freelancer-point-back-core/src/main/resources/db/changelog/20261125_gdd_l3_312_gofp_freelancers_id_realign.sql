-- gdd-l3-312 : gofp_freelancers.id realigned on core_freelancer_id (invariant H9:
-- tnt_delivery_persons.id == freelancer_profiles.id == gofp_freelancers.id == core_freelancer_id).
--
-- Diagnostic (RAISE NOTICE): SELECT count(*) FROM gofp_freelancers WHERE id <> core_freelancer_id.
-- Foreign keys on gofp_freelancers(id) (delivery_needs.assigned_freelancer_id, gofp_need_invitations, ...)
-- are read from pg_constraint, dropped, rewritten and re-created (pattern of gdd-l3-310).
-- Columns holding a freelancer id without a declared foreign key are rewritten too when they exist:
-- announcements.assigned_freelancer_id, deliveries.freelancer_id, gofreelancer_vehicles.freelancer_id,
-- gofreelancer_onboarding_docs.freelancer_id, and the delivery-core copies of the Go id
-- tnt_announcement_responses.delivery_person_id, tnt_deliveries.delivery_person_id (a value equal to the old
-- random id only; each column counted by RAISE NOTICE; a missing table or column is skipped).
-- Idempotent: once realigned no row has id <> core_freelancer_id and the block does nothing.
DO $$
DECLARE
    fk          RECORD;
    plain       RECORD;
    n_rows      BIGINT;
    n_diverging BIGINT;
    n_mapped    BIGINT;
BEGIN
    SELECT count(*) INTO n_diverging FROM gofp_freelancers WHERE id <> core_freelancer_id;
    RAISE NOTICE 'gdd-l3-312 diagnostic: % gofp_freelancers rows with id <> core_freelancer_id', n_diverging;
    IF n_diverging = 0 THEN
        RETURN;
    END IF;

    CREATE TEMP TABLE _gdd3_freelancer_ids ON COMMIT DROP AS
    SELECT f.id AS old_id, f.core_freelancer_id AS new_id
      FROM gofp_freelancers f
     WHERE f.id <> f.core_freelancer_id
       AND NOT EXISTS (SELECT 1 FROM gofp_freelancers o WHERE o.id = f.core_freelancer_id);
    SELECT count(*) INTO n_mapped FROM _gdd3_freelancer_ids;
    RAISE NOTICE 'gdd-l3-312: % rows realigned, % rows left (target id already used)',
        n_mapped, n_diverging - n_mapped;

    CREATE TEMP TABLE _gdd3_freelancer_fks ON COMMIT DROP AS
    SELECT c.conrelid::regclass::text  AS child,
           c.conname                   AS name,
           a.attname                   AS col,
           pg_get_constraintdef(c.oid) AS def
      FROM pg_constraint c
      JOIN pg_attribute a  ON a.attrelid = c.conrelid  AND a.attnum = c.conkey[1]
      JOIN pg_attribute ra ON ra.attrelid = c.confrelid AND ra.attnum = c.confkey[1]
     WHERE c.contype = 'f'
       AND c.confrelid = 'gofp_freelancers'::regclass
       AND ra.attname = 'id'
       AND array_length(c.conkey, 1) = 1;

    FOR fk IN SELECT * FROM _gdd3_freelancer_fks LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.child, fk.name);
    END LOOP;

    UPDATE gofp_freelancers f SET id = m.new_id FROM _gdd3_freelancer_ids m WHERE f.id = m.old_id;
    FOR fk IN SELECT * FROM _gdd3_freelancer_fks LOOP
        EXECUTE format('UPDATE %s t SET %I = m.new_id FROM _gdd3_freelancer_ids m WHERE t.%I = m.old_id',
                       fk.child, fk.col, fk.col);
    END LOOP;

    FOR plain IN
        SELECT v.tbl, v.col
          FROM (VALUES ('announcements', 'assigned_freelancer_id'),
                       ('deliveries', 'freelancer_id'),
                       ('gofreelancer_vehicles', 'freelancer_id'),
                       ('gofreelancer_onboarding_docs', 'freelancer_id'),
                       ('tnt_announcement_responses', 'delivery_person_id'),
                       ('tnt_deliveries', 'delivery_person_id')) AS v(tbl, col)
          JOIN information_schema.columns ic
            ON ic.table_schema = current_schema() AND ic.table_name = v.tbl AND ic.column_name = v.col
    LOOP
        EXECUTE format('UPDATE %I t SET %I = m.new_id FROM _gdd3_freelancer_ids m WHERE t.%I = m.old_id',
                       plain.tbl, plain.col, plain.col);
        GET DIAGNOSTICS n_rows = ROW_COUNT;
        RAISE NOTICE 'gdd-l3-312: %.% rewritten on % rows', plain.tbl, plain.col, n_rows;
    END LOOP;

    FOR fk IN SELECT * FROM _gdd3_freelancer_fks LOOP
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', fk.child, fk.name, fk.def);
    END LOOP;
END $$;
