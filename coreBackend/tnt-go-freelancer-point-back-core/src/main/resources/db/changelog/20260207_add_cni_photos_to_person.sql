-- liquibase formatted sql
-- changeset TiiBnTickTeam:20260207-add-cni-photos-to-person
ALTER TABLE persons ADD COLUMN IF NOT EXISTS cni_recto VARCHAR(255);
ALTER TABLE persons ADD COLUMN IF NOT EXISTS cni_verso VARCHAR(255);
