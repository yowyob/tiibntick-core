-- liquibase formatted sql
-- changeset TiiBnTickTeam:20260208-add-nui-is-active-to-person
ALTER TABLE persons ADD COLUMN IF NOT EXISTS nui VARCHAR(255);
ALTER TABLE persons ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT FALSE;
