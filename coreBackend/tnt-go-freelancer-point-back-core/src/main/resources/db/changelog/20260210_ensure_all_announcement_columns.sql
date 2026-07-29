-- liquibase formatted sql
-- changeset TiiBnTickTeam:20260210-ensure-all-announcement-columns
-- Ensure all required columns exist in announcements table to match Announcement.java model

-- Core columns (should already exist from create-announcement.sql)
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS id UUID;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS client_id UUID;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS packet_id UUID;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS pickup_address_id UUID;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS delivery_address_id UUID;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS status VARCHAR(50);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Recipient information columns
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS recipient_firstname VARCHAR(255);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS recipient_lastname VARCHAR(255);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS recipient_number VARCHAR(255);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS recipient_email VARCHAR(255);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(255);

-- Shipper information columns
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS shipper_firstname VARCHAR(255);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS shipper_lastname VARCHAR(255);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS shipper_email VARCHAR(255);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS shipper_phone VARCHAR(255);

-- Payment and logistics columns
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS amount REAL;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS signature_url TEXT;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS payment_method VARCHAR(100);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS transport_method VARCHAR(100);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS distance DOUBLE PRECISION;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS duration INTEGER;
