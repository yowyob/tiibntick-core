-- gdd-l3-331 : the courier's vehicle moves to tnt-resource-core (H10, lot L3, after gdd-l3-312).
-- gofreelancer_vehicles.core_vehicle_id held ids that point to no tnt_freelancer_vehicles row (never written
-- there): they are reset to NULL. A core vehicle cannot be recreated from the local row: tnt-resource-core
-- requires a maximum capacity (max_capacity_kg > 0, maintainer decision) and no Go table holds one. Those rows
-- are left pending: the courier's next PUT /me/vehicle (capacity now mandatory) creates his core vehicle.
-- Diagnostic (RAISE NOTICE): references reset, rows already delegated, rows pending with and without plate.
-- Guarded on the presence of tnt_freelancer_vehicles (tnt-resource-core) so the Go changelog stays standalone.
DO $$
DECLARE
    n_reset          BIGINT := 0;
    n_delegated      BIGINT := 0;
    n_pending_plate  BIGINT;
    n_pending_none   BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'tnt_freelancer_vehicles') THEN
        UPDATE gofreelancer_vehicles v
           SET core_vehicle_id = NULL
         WHERE v.core_vehicle_id IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM tnt_freelancer_vehicles c WHERE c.id = v.core_vehicle_id);
        GET DIAGNOSTICS n_reset = ROW_COUNT;
        SELECT count(*) INTO n_delegated FROM gofreelancer_vehicles WHERE core_vehicle_id IS NOT NULL;
    ELSE
        RAISE NOTICE 'gdd-l3-331: tnt_freelancer_vehicles absent, references left untouched';
    END IF;

    SELECT count(*) INTO n_pending_plate FROM gofreelancer_vehicles
     WHERE core_vehicle_id IS NULL AND plate_number IS NOT NULL AND btrim(plate_number) <> '';
    SELECT count(*) INTO n_pending_none FROM gofreelancer_vehicles
     WHERE core_vehicle_id IS NULL AND (plate_number IS NULL OR btrim(plate_number) = '');
    RAISE NOTICE 'gdd-l3-331: % fictitious core_vehicle_id reset; % vehicles delegated; pending re-declaration (no capacity known): % with plate, % without plate',
        n_reset, n_delegated, n_pending_plate, n_pending_none;
END $$;
