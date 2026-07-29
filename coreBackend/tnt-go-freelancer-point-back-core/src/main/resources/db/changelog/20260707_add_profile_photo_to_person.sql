-- Add profile photo column to persons table
ALTER TABLE persons ADD COLUMN IF NOT EXISTS profile_photo VARCHAR;
