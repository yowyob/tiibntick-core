-- Add role column to persons table (nullable for backward compatibility)
ALTER TABLE persons ADD COLUMN IF NOT EXISTS role VARCHAR(50);

-- Make national_id and photo_card nullable for admin user
ALTER TABLE persons ALTER COLUMN national_id DROP NOT NULL;
ALTER TABLE persons ALTER COLUMN photo_card DROP NOT NULL;
