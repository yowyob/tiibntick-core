-- liquibase formatted sql
-- changeset tiibntick:rename-destination-vehicle-id-to-relay-point-id
ALTER TABLE announcements RENAME COLUMN destination_vehicle_id TO destination_relay_point_id;
