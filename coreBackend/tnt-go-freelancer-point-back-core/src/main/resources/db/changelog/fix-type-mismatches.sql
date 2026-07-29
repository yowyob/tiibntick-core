-- =====================================================
-- Fix type mismatches between DB schema and Java models
-- =====================================================

-- 1. delivery_persons: Rename longitude_dd/latitude_dd to longitude_gps/latitude_gps
--    and change type from DOUBLE PRECISION to REAL (Float in Java)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='delivery_persons' AND column_name='longitude_dd')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='delivery_persons' AND column_name='longitude_gps') THEN
        ALTER TABLE delivery_persons RENAME COLUMN longitude_dd TO longitude_gps;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='delivery_persons' AND column_name='latitude_dd')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='delivery_persons' AND column_name='latitude_gps') THEN
        ALTER TABLE delivery_persons RENAME COLUMN latitude_dd TO latitude_gps;
    END IF;
END $$;

-- Use DO block for type changes too, in case columns don't exist yet
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='delivery_persons' AND column_name='longitude_gps') THEN
        ALTER TABLE delivery_persons ALTER COLUMN longitude_gps TYPE REAL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='delivery_persons' AND column_name='latitude_gps') THEN
        ALTER TABLE delivery_persons ALTER COLUMN latitude_gps TYPE REAL;
    END IF;
END $$;

-- 2. delivery_persons: Change siret from DOUBLE PRECISION to VARCHAR
--    (SIRET is a 14-digit French business identifier, not a number)
ALTER TABLE delivery_persons ALTER COLUMN siret TYPE VARCHAR USING siret::VARCHAR;

-- 3. announcements: Change amount from REAL to DOUBLE PRECISION
ALTER TABLE announcements ALTER COLUMN amount TYPE DOUBLE PRECISION;

-- 4. deliveries: Change note_livreur from REAL to DOUBLE PRECISION
ALTER TABLE deliveries ALTER COLUMN note_livreur TYPE DOUBLE PRECISION;
