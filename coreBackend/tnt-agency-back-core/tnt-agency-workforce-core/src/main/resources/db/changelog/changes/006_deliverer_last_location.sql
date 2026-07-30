--liquibase formatted sql
--changeset jeff-belekotan:006_deliverer_last_location
--comment: Last known GPS for agency deliverers (UC-52)

ALTER TABLE agency_hr.deliverers
    ADD COLUMN IF NOT EXISTS last_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS last_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS last_accuracy_meters DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS last_location_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_mission_id UUID;

--rollback ALTER TABLE agency_hr.deliverers DROP COLUMN IF EXISTS last_latitude, DROP COLUMN IF EXISTS last_longitude, DROP COLUMN IF EXISTS last_accuracy_meters, DROP COLUMN IF EXISTS last_location_at, DROP COLUMN IF EXISTS last_mission_id;
