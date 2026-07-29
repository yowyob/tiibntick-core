-- liquibase formatted sql
-- changeset TiiBnTickTeam:20260216-fix-packet-circular-dependency
-- Drop announcement_id from packets to fix circular dependency and not-null constraint error

ALTER TABLE packets DROP COLUMN IF EXISTS announcement_id;
