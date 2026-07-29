-- liquibase formatted sql
-- changeset TiiBnTickTeam:20260216-drop-recipient-number
-- Drop recipient_number column from announcements table

ALTER TABLE announcements DROP COLUMN IF EXISTS recipient_number;
