--liquibase formatted sql

--changeset tiibntick:20260218-add-height-to-packets
ALTER TABLE packets ADD COLUMN IF NOT EXISTS height DOUBLE PRECISION;
