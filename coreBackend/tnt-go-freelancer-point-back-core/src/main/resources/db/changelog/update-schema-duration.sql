-- Add duration column to announcements table
ALTER TABLE announcements
ADD COLUMN IF NOT EXISTS duration INTEGER;
-- Add duration column to deliveries table
ALTER TABLE deliveries
ADD COLUMN IF NOT EXISTS duration INTEGER;