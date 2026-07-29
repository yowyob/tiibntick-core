-- liquibase formatted sql
-- changeset TiiBnTickTeam:20260208-fix-packets-and-announcements-schema
DO $$ 
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='packets' AND column_name='perishable')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='packets' AND column_name='is_perishable') THEN
        ALTER TABLE packets RENAME COLUMN perishable TO is_perishable;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='announcements' AND column_name='price')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='announcements' AND column_name='amount') THEN
        ALTER TABLE announcements RENAME COLUMN price TO amount;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='packets' AND column_name='weight') THEN
        ALTER TABLE packets ALTER COLUMN weight DROP NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='packets' AND column_name='height') THEN
        ALTER TABLE packets ALTER COLUMN height DROP NOT NULL;
    END IF;
END $$;

ALTER TABLE announcements ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(255);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS recipient_number VARCHAR(255);
