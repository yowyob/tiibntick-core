--liquibase formatted sql
--changeset TiiBnTickTeam:add-thickness-remove-price
ALTER TABLE packets
ADD COLUMN IF NOT EXISTS thickness DOUBLE PRECISION;
ALTER TABLE announcements DROP COLUMN IF EXISTS price;