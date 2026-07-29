-- liquibase formatted sql
-- changeset TiiBnTickTeam:20260209-separate-recipient-names-and-mandatory-emails
-- Migration to split recipient name and make emails mandatory in announcements table

-- 1. Add new columns for recipient names
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS recipient_firstname VARCHAR(255);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS recipient_lastname VARCHAR(255);

-- 2. Migrate data from recipient_name if it exists
DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='announcements' AND column_name='recipient_name') THEN
        UPDATE announcements SET 
            recipient_firstname = split_part(recipient_name, ' ', 1),
            recipient_lastname = substring(recipient_name from position(' ' in recipient_name) + 1);
        
        -- 3. Drop recipient_name
        ALTER TABLE announcements DROP COLUMN recipient_name;
    END IF;
END $$;

-- 4. Ensure shipper_email and recipient_email are mandatory
-- Note: We assume they are already populated or will be handled during creation.
-- In a real scenario, we might need a default value for existing rows.
ALTER TABLE announcements ALTER COLUMN shipper_email SET NOT NULL;
ALTER TABLE announcements ALTER COLUMN recipient_email SET NOT NULL;
