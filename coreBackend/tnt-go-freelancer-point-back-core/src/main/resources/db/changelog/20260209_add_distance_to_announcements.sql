-- Migration to add distance column to announcements table
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS distance DOUBLE PRECISION;
