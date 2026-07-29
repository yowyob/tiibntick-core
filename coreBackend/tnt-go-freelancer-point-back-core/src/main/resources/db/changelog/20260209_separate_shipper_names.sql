-- Migration to separate shipper names in announcements table
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS shipper_firstname VARCHAR(255);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS shipper_lastname VARCHAR(255);

-- Migrate data from shipper_name if it exists
DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='announcements' AND column_name='shipper_name') THEN
        UPDATE announcements SET 
            shipper_firstname = split_part(shipper_name, ' ', 1),
            shipper_lastname = substring(shipper_name from position(' ' in shipper_name) + 1);
        
        ALTER TABLE announcements DROP COLUMN shipper_name;
    END IF;
END $$;
