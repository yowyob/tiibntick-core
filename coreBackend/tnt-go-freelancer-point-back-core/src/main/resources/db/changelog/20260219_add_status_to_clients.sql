-- Add status column to clients table (nullable, defaults to 'ACTIVE')
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'clients' AND column_name = 'status') THEN
        ALTER TABLE clients ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE';
    END IF;
END $$;

-- Update existing clients to have ACTIVE status
UPDATE clients SET status = 'ACTIVE' WHERE status IS NULL;
