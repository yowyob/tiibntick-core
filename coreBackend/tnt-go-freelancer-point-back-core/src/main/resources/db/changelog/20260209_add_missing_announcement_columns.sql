-- liquibase formatted sql
-- changeset TiiBnTickTeam:20260209-add-missing-announcement-columns
-- Add missing columns to announcements table to match the Java model

ALTER TABLE announcements ADD COLUMN IF NOT EXISTS signature_url TEXT;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS payment_method VARCHAR(100);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS transport_method VARCHAR(100);
