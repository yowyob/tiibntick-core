-- The destination relay point a sender chose for a Go delivery need was accepted by the API but
-- never stored, so the relay point could not see the parcels it should receive.
-- target_relay_point_id  : gofp relay point id or its hub id (NULL for a door-to-door need).
-- requested_storage_days : storage duration asked by the sender at the relay point.
-- Additive and idempotent; existing rows keep NULL.
ALTER TABLE delivery_needs ADD COLUMN IF NOT EXISTS target_relay_point_id UUID;
ALTER TABLE delivery_needs ADD COLUMN IF NOT EXISTS requested_storage_days INTEGER;

CREATE INDEX IF NOT EXISTS idx_delivery_needs_target_relay_point_id
    ON delivery_needs (target_relay_point_id) WHERE target_relay_point_id IS NOT NULL;
